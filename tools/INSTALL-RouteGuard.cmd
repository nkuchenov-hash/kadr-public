@echo off
chcp 65001 >nul
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0KADR-Amnezia-RouteGuard.ps1" -Install
if errorlevel 1 echo ERROR: Run as administrator.
pause
