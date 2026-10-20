@echo off
cd /d "%~dp0"
.venv\Scripts\python.exe scripts\executar_bar.py --treinamento --parar
pause
