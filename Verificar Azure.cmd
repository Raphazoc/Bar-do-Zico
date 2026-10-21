@echo off
cd /d "%~dp0"
.venv\Scripts\python.exe scripts\verificar_azure.py --login
pause
