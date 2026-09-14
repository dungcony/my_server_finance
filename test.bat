@echo off
setlocal
chcp 65001 >nul
title Finance AI Server Test Runner

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test.ps1" %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Lỗi] Quá trình kiểm thử kết thúc với mã lỗi: %ERRORLEVEL%
)

echo.
pause
endlocal
