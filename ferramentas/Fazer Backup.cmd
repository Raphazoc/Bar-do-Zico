@echo off
cd /d "%~dp0.."
.venv\Scripts\python.exe scripts\backup_banco.py --verificar-restauracao
pause
