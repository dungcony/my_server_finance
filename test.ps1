<#
.SYNOPSIS
    Script chay kiem thu (Unit Test & Integration Test) cho Finance AI Server.
.DESCRIPTION
    - Tu dong nhan dien thu muc backend.
    - Ho tro chay nhanh Unit Test khong can bat Docker.
    - Nhan dien Docker ca tren Windows lan trong WSL.
    - Ho tro chay tung file test cu the.
.PARAMETER Unit
    Chi chay Unit Test (nhanh, khong can Docker).
.PARAMETER All
    Chay toan bo test suite.
.PARAMETER Integration
    Chi chay Integration Test.
.PARAMETER Name
    Ten class test muon chay (vi du: JwtServiceTest).
.PARAMETER Force
    Bo qua moi kiem tra Docker va chay thang lenh test.
.EXAMPLE
    .\test.ps1
    .\test.ps1 -Unit
    .\test.ps1 -All
    .\test.ps1 -Name JwtServiceTest
#>

param (
    [switch]$Unit,
    [switch]$All,
    [switch]$Integration,
    [string]$Name,
    [switch]$Force,
    [switch]$Help
)

if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

$ErrorActionPreference = "Stop"

# 1. Xac dinh thu muc backend chua pom.xml
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) {
    $ScriptDir = (Get-Location).Path
}

$ServerDir = $null
if (Test-Path (Join-Path $ScriptDir "pom.xml")) {
    $ServerDir = $ScriptDir
} elseif (Test-Path (Join-Path $ScriptDir "source\finance-ai-server\pom.xml")) {
    $ServerDir = (Join-Path $ScriptDir "source\finance-ai-server")
} else {
    Write-Host "[ERROR] Khong tim thay thu muc backend chua pom.xml!" -ForegroundColor Red
    exit 1
}

Set-Location $ServerDir

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "       FINANCE AI SERVER - TEST RUNNER                " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 2. Kiem tra Maven
try {
    $mvnVer = mvn -v 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or -not $mvnVer) {
        throw "Maven khong kha dung."
    }
    $firstMvnLine = ($mvnVer -split "`r?`n")[0]
    Write-Host "[OK] Maven: $firstMvnLine" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Khong tim thay Maven trong PATH!" -ForegroundColor Red
    exit 1
}

# 3. Kiem tra Docker (Ho tro ca Windows va WSL)
function Check-DockerStatus {
    # 3.1 Kiem tra Docker tren Windows
    $winDocker = $false
    try {
        $dockerCmd = "docker"
        if (-not (Get-Command "docker" -ErrorAction SilentlyContinue)) {
            $defaultDockerPath = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"
            if (Test-Path $defaultDockerPath) {
                $dockerCmd = $defaultDockerPath
            }
        }
        $res = & $dockerCmd info 2>&1 | Out-String
        if ($res -and $res -notmatch "failed to connect|cannot find the file|error during connect") {
            $winDocker = $true
        }
    } catch {
        $winDocker = $false
    }

    if ($winDocker) {
        return @{ Found = $true; Type = "WINDOWS" }
    }

    # 3.2 Kiem tra Docker trong WSL
    try {
        if (Get-Command "wsl" -ErrorAction SilentlyContinue) {
            $wslDockerRes = wsl -e docker ps 2>&1 | Out-String
            if ($wslDockerRes -match "CONTAINER ID") {
                return @{ Found = $true; Type = "WSL" }
            }
        }
    } catch {
        # ignore
    }

    return @{ Found = $false; Type = "NONE" }
}

# 4. Xac dinh che do test
$testMode = ""
$targetTestPattern = ""

if ($Name) {
    $targetTestPattern = $Name
    $testMode = "CUSTOM"
} elseif ($Unit) {
    $testMode = "UNIT"
} elseif ($Integration) {
    $testMode = "INTEGRATION"
} elseif ($All) {
    $testMode = "ALL"
} else {
    Write-Host ""
    Write-Host "Chon che do kiem thu:" -ForegroundColor Yellow
    Write-Host "  1. Chi chay Unit Test (Nhanh, KHONG can Docker) - Mac dinh" -ForegroundColor White
    Write-Host "  2. Chay toan bo Test Suite" -ForegroundColor White
    Write-Host "  3. Chi chay Integration Test" -ForegroundColor White
    Write-Host "  4. Chay mot Test class cu the theo ten" -ForegroundColor White
    Write-Host ""
    $choice = Read-Host "Nhap lua chon (1/2/3/4) [Mac dinh: 1]"

    switch ($choice.Trim()) {
        "2" { $testMode = "ALL" }
        "3" { $testMode = "INTEGRATION" }
        "4" {
            $testMode = "CUSTOM"
            $targetTestPattern = Read-Host "Nhap ten class test (vi du: JwtServiceTest)"
            if ([string]::IsNullOrWhiteSpace($targetTestPattern)) {
                Write-Host "[ERROR] Ten test khong duoc de trong!" -ForegroundColor Red
                exit 1
            }
        }
        default { $testMode = "UNIT" }
    }
}

# 5. Kiem tra Docker neu can chay Integration Test
if ($testMode -in @("ALL", "INTEGRATION") -and -not $Force) {
    Write-Host ""
    Write-Host "[Dang kiem tra Docker...]" -ForegroundColor Gray
    $dockerInfo = Check-DockerStatus
    if ($dockerInfo.Found) {
        if ($dockerInfo.Type -eq "WSL") {
            try {
                $wslIp = (wsl -e hostname -I).Trim().Split(" ")[0]
                $env:DOCKER_HOST = "tcp://${wslIp}:2375"
                $env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = "/var/run/docker.sock"
                
                # Tu dong dong bo vao ~/.testcontainers.properties de moi IDE va Maven deu nhan
                $tcFile = [System.IO.Path]::Combine($env:USERPROFILE, ".testcontainers.properties")
                $tcContent = "docker.host=tcp\://${wslIp}\:2375`ntestcontainers.reuse.enable=true`n"
                [System.IO.File]::WriteAllText($tcFile, $tcContent)

                Write-Host "[OK] Da ket noi Docker trong WSL qua tcp://${wslIp}:2375" -ForegroundColor Green
            } catch {
                Write-Host "[CANH BAO] Khong the lay IP WSL, thu dung localhost:2375" -ForegroundColor Yellow
                $env:DOCKER_HOST = "tcp://127.0.0.1:2375"
            }
        } else {
            Write-Host "[OK] Docker Engine tren Windows dang hoat dong tot." -ForegroundColor Green
        }
    } else {
        Write-Host "[CANH BAO] Khong phat hien Docker dang chay tren Windows hoac WSL!" -ForegroundColor Yellow
        Write-Host "Integration Test su dung Testcontainers nen can Docker de khoi tao DB test." -ForegroundColor Yellow
    }
}

# 6. Thiet lap tham so Maven Surefire
$surefirePattern = ""
switch ($testMode) {
    "UNIT" {
        Write-Host ""
        Write-Host "-> Dang chay: UNIT TESTS (bo qua Integration Tests)" -ForegroundColor Cyan
        $surefirePattern = "*Test,!*IntegrationTest"
    }
    "INTEGRATION" {
        Write-Host ""
        Write-Host "-> Dang chay: INTEGRATION TESTS" -ForegroundColor Cyan
        $surefirePattern = "*IntegrationTest"
    }
    "CUSTOM" {
        Write-Host ""
        Write-Host "-> Dang chay test: $targetTestPattern" -ForegroundColor Cyan
        $surefirePattern = $targetTestPattern
    }
    "ALL" {
        Write-Host ""
        Write-Host "-> Dang chay: TOAN BO CAC TEST" -ForegroundColor Cyan
        $surefirePattern = ""
    }
}

Write-Host "------------------------------------------------------" -ForegroundColor Cyan
if ($surefirePattern) {
    Write-Host "Lenh thuc thi: mvn test -Dtest=`"$surefirePattern`"" -ForegroundColor Gray
    & mvn test "-Dtest=$surefirePattern"
} else {
    Write-Host "Lệnh thuc thi: mvn test" -ForegroundColor Gray
    & mvn test
}

$exitCode = $LASTEXITCODE
Write-Host "------------------------------------------------------" -ForegroundColor Cyan
if ($exitCode -eq 0) {
    Write-Host "[SUCCESS] Kiem thu hoan thanh thanh cong!" -ForegroundColor Green
} else {
    Write-Host "[FAILED] Kiem thu that bai (Exit code: $exitCode)." -ForegroundColor Red
}

exit $exitCode
