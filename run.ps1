<#
.SYNOPSIS
    Script chạy ứng dụng Spring Boot tương tự như nút Run của IntelliJ IDEA.
.DESCRIPTION
    - Tự động nhận diện thư mục backend (source/finance-ai-server) dù đứng ở bất kỳ đâu.
    - Kiểm tra Java (JDK 17) và Maven.
    - Kiểm tra và đảm bảo file .env đã sẵn sàng.
    - Tự động phát hiện xung đột cổng 8080 (tránh lỗi Port already in use) và hỗ trợ giải phóng cổng.
    - Kiểm tra nhanh kết nối PostgreSQL (5432) và Redis (6379).
    - Khởi chạy Spring Boot với profile dev (hoặc tuỳ chọn).
.PARAMETER Profile
    Spring profile muốn chạy (mặc định: dev).
.PARAMETER Clean
    Chạy clean trước khi compile/run (tương tự Rebuild Project trong IntelliJ).
.PARAMETER DebugMode
    Bật cổng Remote Debug 5005 (để attach debugger từ IDE).
.PARAMETER Test
    Chạy test suite (`mvn test`).
.PARAMETER KillPort
    Tự động tắt tiến trình chiếm cổng 8080 mà không cần hỏi lại.
.EXAMPLE
    .\run.ps1
    .\run.ps1 -Clean
    .\run.ps1 -DebugMode
    .\run.ps1 -Profile dev
    .\run.ps1 -KillPort
#>

param (
    [string]$Profile = "dev",
    [switch]$Clean,
    [Alias("Debug")]
    [switch]$DebugMode,
    [switch]$Test,
    [switch]$KillPort,
    [switch]$Help
)

if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

$ErrorActionPreference = "Stop"

# 1. Xác định thư mục backend chứa pom.xml
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) {
    $ScriptDir = (Get-Location).Path
}

$ServerDir = $null
if (Test-Path (Join-Path $ScriptDir "pom.xml")) {
    $ServerDir = $ScriptDir
} elseif (Test-Path (Join-Path $ScriptDir "source\finance-ai-server\pom.xml")) {
    $ServerDir = (Join-Path $ScriptDir "source\finance-ai-server")
} elseif (Test-Path "source\finance-ai-server\pom.xml") {
    $ServerDir = (Resolve-Path "source\finance-ai-server").Path
} else {
    Write-Host "[ERROR] Không tìm thấy thư mục backend chứa pom.xml!" -ForegroundColor Red
    exit 1
}

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "       FINANCE AI SERVER - SPRING BOOT RUNNER         " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "Backend dir   : $ServerDir" -ForegroundColor Gray
Write-Host "Spring profile: $Profile" -ForegroundColor Gray

# 2. Kiểm tra Java & Maven
try {
    $javaVer = java -version 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or -not $javaVer) {
        throw "Java không khả dụng."
    }
    $firstLine = ($javaVer -split "`r?`n")[0]
    Write-Host "[OK] Java: $firstLine" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Không tìm thấy Java! Vui lòng kiểm tra JDK 17 trong PATH." -ForegroundColor Red
    exit 1
}

try {
    $mvnVer = mvn -v 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or -not $mvnVer) {
        throw "Maven không khả dụng."
    }
    $firstMvnLine = ($mvnVer -split "`r?`n")[0]
    Write-Host "[OK] Maven: $firstMvnLine" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Không tìm thấy Maven! Vui lòng kiểm tra Maven 3.9+ trong PATH." -ForegroundColor Red
    exit 1
}

# 3. Kiểm tra file .env
$envFile = Join-Path $ServerDir ".env"
$rootEnvFile = Join-Path $ScriptDir ".env"

if (-not (Test-Path $envFile)) {
    if (Test-Path $rootEnvFile) {
        Write-Host "[INFO] Copy .env từ root sang backend..." -ForegroundColor Yellow
        Copy-Item $rootEnvFile $envFile
    } elseif (Test-Path (Join-Path $ServerDir ".env.example")) {
        Write-Host "[WARNING] Chưa có file .env, tự động tạo từ .env.example..." -ForegroundColor Yellow
        Copy-Item (Join-Path $ServerDir ".env.example") $envFile
        Write-Host "[LƯU Ý] Hãy cập nhật JWT_SECRET trong $envFile nếu cần!" -ForegroundColor Magenta
    }
} else {
    Write-Host "[OK] Đã tìm thấy file .env" -ForegroundColor Green
}

# 4. Kiểm tra xung đột cổng 8080
$occupied = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($occupied) {
    $pidToKill = $occupied[0].OwningProcess
    $pName = (Get-Process -Id $pidToKill -ErrorAction SilentlyContinue).ProcessName
    Write-Host "[CẢNH BÁO] Cổng 8080 đang bị chiếm bởi: $pName (PID: $pidToKill)" -ForegroundColor Yellow

    if ($KillPort) {
        Write-Host "[XỬ LÝ] Đang dừng tiến trình PID $pidToKill để giải phóng cổng 8080..." -ForegroundColor Yellow
        Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        Write-Host "[OK] Đã giải phóng cổng 8080." -ForegroundColor Green
    } else {
        $confirm = Read-Host "Bạn có muốn tắt tiến trình cũ để tiếp tục không? (Y/N) [Mặc định: Y]"
        if ([string]::IsNullOrWhiteSpace($confirm) -or $confirm -match "^[Yy]") {
            Stop-Process -Id $pidToKill -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
            Write-Host "[OK] Đã giải phóng cổng 8080." -ForegroundColor Green
        } else {
            Write-Host "[DỪNG] Vui lòng giải phóng cổng 8080 trước khi chạy." -ForegroundColor Red
            exit 1
        }
    }
}

# 5. Kiểm tra nhanh cổng PostgreSQL (5432) & Redis (6379)
function Test-PortQuickly([string]$hostName, [int]$port) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $iar = $tcp.BeginConnect($hostName, $port, $null, $null)
        $wait = $iar.AsyncWaitHandle.WaitOne(800, $false)
        if ($wait -and $tcp.Connected) {
            $tcp.EndConnect($iar)
            $tcp.Close()
            return $true
        }
        $tcp.Close()
        return $false
    } catch {
        return $false
    }
}

if (Test-PortQuickly "127.0.0.1" 5432) {
    Write-Host "[OK] PostgreSQL đang chạy tại port 5432" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] PostgreSQL (port 5432) chưa bật hoặc chưa kết nối được!" -ForegroundColor Yellow
    Write-Host "          - Nếu dùng Docker: cd source\finance-ai-server; docker compose up -d" -ForegroundColor Yellow
    Write-Host "          - Nếu cài trực tiếp trên Windows: Khởi động service PostgreSQL." -ForegroundColor Yellow
}

if (Test-PortQuickly "127.0.0.1" 6379) {
    Write-Host "[OK] Redis đang chạy tại port 6379" -ForegroundColor Green
} else {
    Write-Host "[CẢNH BÁO] Redis (port 6379) chưa bật! (Cần nếu dùng tính năng cache/session)" -ForegroundColor DarkYellow
}

# 6. Thực thi Maven
Set-Location $ServerDir

if ($Test) {
    Write-Host "------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "Đang chạy test suite..." -ForegroundColor Cyan
    Write-Host "------------------------------------------------------" -ForegroundColor Cyan
    mvn test
    exit $LASTEXITCODE
}

$mavenArgs = @("spring-boot:run", "-Dspring-boot.run.profiles=$Profile")

if ($Clean) {
    $mavenArgs = @("clean") + $mavenArgs
}

if ($DebugMode) {
    Write-Host "[DEBUG MODE] Cổng remote debug mở tại localhost:5005" -ForegroundColor Magenta
    $mavenArgs += "-Dspring-boot.run.jvmArguments=`"-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005`""
}

Write-Host "------------------------------------------------------" -ForegroundColor Cyan
Write-Host "Lệnh chạy: mvn $($mavenArgs -join ' ')" -ForegroundColor Cyan
Write-Host "Nhấn Ctrl + C để dừng ứng dụng." -ForegroundColor Gray
Write-Host "------------------------------------------------------" -ForegroundColor Cyan

& mvn $mavenArgs
