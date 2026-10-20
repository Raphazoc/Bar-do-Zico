@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\iniciar-bar.ps1
if errorlevel 1 pause
