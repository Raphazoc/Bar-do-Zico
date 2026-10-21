@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
cd /d "%~dp0"
.venv\Scripts\python.exe scripts\android_de_teste.py
if errorlevel 1 pause
