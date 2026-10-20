$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$restoreRoot = Split-Path -Parent $PSScriptRoot
$restorePicker = New-Object System.Windows.Forms.OpenFileDialog
$restorePicker.Title = 'Escolha um backup do Bar do Zico para conferir em banco separado'
$restorePicker.Filter = 'Backup PostgreSQL (*.dump)|*.dump'
$restorePicker.InitialDirectory = Join-Path $restoreRoot '.tools/backups'
if ($restorePicker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { return }
& (Join-Path $restoreRoot '.venv/Scripts/python.exe') (Join-Path $PSScriptRoot 'backup_banco.py') --conferir-arquivo $restorePicker.FileName
if ($LASTEXITCODE -ne 0) { throw 'A conferência do backup não foi concluída. O banco real não foi substituído.' }
