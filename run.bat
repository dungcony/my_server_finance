@echo off
setlocal
chcp 65001 >nul
title Finance AI Server Runner

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [Loi] Chuong trinh ket thuc voi ma loi: %ERRORLEVEL%
)
endlocal
