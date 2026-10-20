@echo off
cd /d "%~dp0"
if exist ".tools\training\acesso.txt" (notepad ".tools\training\acesso.txt") else (echo Inicie o treinamento primeiro.& pause)
