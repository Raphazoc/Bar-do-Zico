$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$backupRoot = Split-Path -Parent $PSScriptRoot
$backupPicker = New-Object System.Windows.Forms.FolderBrowserDialog
$backupPicker.Description = 'Escolha a pasta para uma segunda cópia dos backups do Bar do Zico (outro disco ou pendrive).'
$backupPicker.ShowNewFolderButton = $true
if ($backupPicker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { Write-Output 'Configuração anterior preservada.'; return }
$backupDestination = Join-Path $backupPicker.SelectedPath 'Bar-do-Zico-Backups'
New-Item -ItemType Directory -Path $backupDestination -Force | Out-Null
& (Join-Path $backupRoot '.venv/Scripts/python.exe') (Join-Path $PSScriptRoot 'backup_automatico.py') --destino $backupDestination --intervalo 60 --ativar
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível salvar a configuração dos backups.' }
Write-Output 'Segundo destino preparado. A cada hora será tentada uma cópia enquanto o sistema estiver em uso.'
