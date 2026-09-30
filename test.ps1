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
.PARAMETER Module
    Ten module muon chay test (vi du: user, budget, wallet).
.PARAMETER Force
    Bo qua moi kiem tra Docker va chay thang lenh test.
.EXAMPLE
    .\test.ps1
    .\test.ps1 -Unit
    .\test.ps1 -All
    .\test.ps1 -Name JwtServiceTest
    .\test.ps1 -Module user
#>

param (
    [switch]$Unit,
    [switch]$All,
    [switch]$Integration,
    [string]$Name,
    [string]$Module,
    [switch]$Force,
    [switch]$Help
)

if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

$ErrorActionPreference = "Stop"

# Xac dinh thu muc backend chua pom.xml
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

# Kiem tra Maven
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

# Kiem tra Docker (Ho tro ca Windows va WSL)
function Check-DockerStatus {
    # Kiem tra Docker trong WSL truoc (vi da phan backend dev chay tren WSL)
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

    # Kiem tra Docker tren Windows
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

    return @{ Found = $false; Type = "NONE" }
}

# Chon module tuong tac qua danh sach danh so
function Select-ModuleInteractive {
    param (
        [string]$PromptTitle = "Chon module",
        [bool]$AllowManualInput = $false
    )

    $modulesDir = Join-Path $ServerDir "src\test\java\com\datn\financeapp"
    if (-not (Test-Path $modulesDir)) {
        Write-Host "[ERROR] Khong tim thay thu muc: $modulesDir" -ForegroundColor Red
        return $null
    }

    $modules = Get-ChildItem -Path $modulesDir -Directory | Sort-Object Name
    if ($modules.Count -eq 0) {
        Write-Host "[CANH BAO] Khong tim thay module nao!" -ForegroundColor Yellow
        return $null
    }

    Write-Host ""
    Write-Host "$PromptTitle`:" -ForegroundColor Yellow
    if ($AllowManualInput) {
        Write-Host "   0. [Tu nhap ten class thu cong]" -ForegroundColor Gray
    }
    for ($i = 0; $i -lt $modules.Count; $i++) {
        $idxStr = ($i + 1).ToString().PadLeft(2, ' ')
        Write-Host "  $idxStr. $($modules[$i].Name)" -ForegroundColor White
    }
    Write-Host ""

    while ($true) {
        $inputVal = Read-Host "Nhap so thu tu module hoac ten module (hoac 'q' de huy)"
        $inputVal = $inputVal.Trim()

        if ($inputVal -ieq "q") {
            Write-Host "[THOAT] Da huy chon." -ForegroundColor Gray
            exit 0
        }

        if ($AllowManualInput -and $inputVal -eq "0") {
            return "MANUAL"
        }

        if ($inputVal -match '^\d+$') {
            $selIndex = [int]$inputVal - 1
            if ($selIndex -ge 0 -and $selIndex -lt $modules.Count) {
                return $modules[$selIndex].Name
            }
        }

        # Kiem tra neu nhap chuoi ten module truc tiep
        $matched = $modules | Where-Object { $_.Name -ieq $inputVal }
        if ($matched) {
            return $matched.Name
        }

        Write-Host "[CANH BAO] Lua chon '$inputVal' khong hop le, vui long chon lai!" -ForegroundColor Yellow
    }
}

# Chon class test qua menu phan cap (Module -> Layer/Muc -> Test Class)
function Select-TestClassInteractive {
    $selectedModule = Select-ModuleInteractive -PromptTitle "Chon module chua test class" -AllowManualInput $true
    if (-not $selectedModule) {
        return $null
    }
    if ($selectedModule -eq "MANUAL") {
        $customName = Read-Host "Nhap ten class test (vi du: JwtServiceTest)"
        return $customName.Trim()
    }

    $modulePath = Join-Path $ServerDir "src\test\java\com\datn\financeapp\$selectedModule"
    
    # Quet cac thu muc con (layer nhu service, controller, repository...)
    $layers = Get-ChildItem -Path $modulePath -Directory | Where-Object {
        (Get-ChildItem -Path $_.FullName -Recurse -Filter "*Test.java" -File).Count -gt 0
    } | Sort-Object Name

    $searchTargetDir = $modulePath
    if ($layers.Count -gt 0) {
        Write-Host ""
        Write-Host "Chon muc trong module '$selectedModule':" -ForegroundColor Yellow
        Write-Host "   0. [Xem tat ca cac class trong module $selectedModule]" -ForegroundColor Gray
        for ($i = 0; $i -lt $layers.Count; $i++) {
            $idxStr = ($i + 1).ToString().PadLeft(2, ' ')
            Write-Host "  $idxStr. $($layers[$i].Name)" -ForegroundColor White
        }
        Write-Host ""

        while ($true) {
            $layerInput = (Read-Host "Nhap lua chon muc (0-$($layers.Count)) [Mac dinh: 0]").Trim()
            if ([string]::IsNullOrWhiteSpace($layerInput) -or $layerInput -eq "0") {
                $searchTargetDir = $modulePath
                break
            }
            if ($layerInput -match '^\d+$') {
                $layerIdx = [int]$layerInput - 1
                if ($layerIdx -ge 0 -and $layerIdx -lt $layers.Count) {
                    $searchTargetDir = $layers[$layerIdx].FullName
                    break
                }
            }
            $matchedLayer = $layers | Where-Object { $_.Name -ieq $layerInput }
            if ($matchedLayer) {
                $searchTargetDir = $matchedLayer.FullName
                break
            }
            Write-Host "[CANH BAO] Lua chon muc khong hop le, vui long nhap lai!" -ForegroundColor Yellow
        }
    }

    # Quet danh sach class test trong thu muc da chon
    $testFiles = Get-ChildItem -Path $searchTargetDir -Recurse -Filter "*Test.java" -File | Sort-Object Name
    if ($testFiles.Count -eq 0) {
        Write-Host "[CANH BAO] Khong tim thay class test nao trong: $searchTargetDir" -ForegroundColor Yellow
        return $null
    }

    Write-Host ""
    Write-Host "Danh sach class test:" -ForegroundColor Yellow
    for ($i = 0; $i -lt $testFiles.Count; $i++) {
        $idxStr = ($i + 1).ToString().PadLeft(2, ' ')
        $className = [System.IO.Path]::GetFileNameWithoutExtension($testFiles[$i].Name)
        Write-Host "  $idxStr. $className" -ForegroundColor White
    }
    Write-Host ""

    while ($true) {
        $classInput = (Read-Host "Nhap so thu tu class test (hoac 'q' de huy)").Trim()
        if ($classInput -ieq "q") {
            Write-Host "[THOAT] Da huy chon." -ForegroundColor Gray
            exit 0
        }
        if ($classInput -match '^\d+$') {
            $cIdx = [int]$classInput - 1
            if ($cIdx -ge 0 -and $cIdx -lt $testFiles.Count) {
                return [System.IO.Path]::GetFileNameWithoutExtension($testFiles[$cIdx].Name)
            }
        }

        # Kiem tra neu nguoi dung nhap truc tiep ten class
        $matchedFile = $testFiles | Where-Object { 
            [System.IO.Path]::GetFileNameWithoutExtension($_.Name) -ieq $classInput 
        }
        if ($matchedFile) {
            return [System.IO.Path]::GetFileNameWithoutExtension($matchedFile.Name)
        }

        Write-Host "[CANH BAO] Lua chon class '$classInput' khong hop le, vui long chon lai!" -ForegroundColor Yellow
    }
}

# Xac dinh che do test
$testMode = ""
$targetTestPattern = ""
$targetModule = ""

if ($Name) {
    $targetTestPattern = $Name
    $testMode = "CUSTOM"
} elseif ($Module) {
    $targetModule = $Module.Trim()
    $targetTestPattern = "com/datn/financeapp/$targetModule/**"
    $testMode = "MODULE"
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
    Write-Host "  5. Chay test theo module" -ForegroundColor White
    Write-Host ""
    $choice = Read-Host "Nhap lua chon (1/2/3/4/5) [Mac dinh: 1]"

    switch ($choice.Trim()) {
        "2" { $testMode = "ALL" }
        "3" { $testMode = "INTEGRATION" }
        "4" {
            $testMode = "CUSTOM"
            $targetTestPattern = Select-TestClassInteractive
            if ([string]::IsNullOrWhiteSpace($targetTestPattern)) {
                Write-Host "[ERROR] Chua chon class test!" -ForegroundColor Red
                exit 1
            }
        }
        "5" {
            $testMode = "MODULE"
            $targetModule = Select-ModuleInteractive -PromptTitle "Chon module muon chay test"
            if ([string]::IsNullOrWhiteSpace($targetModule)) {
                Write-Host "[ERROR] Chua chon module!" -ForegroundColor Red
                exit 1
            }
            $targetTestPattern = "com/datn/financeapp/$targetModule/**"
        }
        default { $testMode = "UNIT" }
    }
}

# Kiem tra Docker neu can chay Integration Test
if ($testMode -in @("ALL", "INTEGRATION", "CUSTOM", "MODULE") -and -not $Force) {
    Write-Host ""
    Write-Host "[Dang kiem tra Docker...]" -ForegroundColor Gray
    $dockerInfo = Check-DockerStatus
    if ($dockerInfo.Found) {
        if ($dockerInfo.Type -eq "WSL") {
            try {
                $wslIp = (wsl -e hostname -I).Trim().Split(" ")[0]
                $env:DOCKER_HOST = "tcp://${wslIp}:2375"
                $env:TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE = "/var/run/docker.sock"
                $env:TESTCONTAINERS_RYUK_DISABLED = "true"
                
                # Tu dong dong bo vao ~/.testcontainers.properties de moi IDE va Maven deu nhan
                $tcFile = [System.IO.Path]::Combine($env:USERPROFILE, ".testcontainers.properties")
                $tcContent = "docker.host=tcp\://${wslIp}\:2375`ntestcontainers.reuse.enable=true`nryuk.disabled=true`n"
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

# Thiet lap tham so Maven Surefire
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
    "MODULE" {
        Write-Host ""
        Write-Host "-> Dang chay test module: $targetModule" -ForegroundColor Cyan
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
