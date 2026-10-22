@echo off
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -STA -File scripts\configurar-backup.ps1
pause
