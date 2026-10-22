@echo off
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\aplicar-rede-local.ps1
pause
