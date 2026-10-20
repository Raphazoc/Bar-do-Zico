param([switch]$Empacotar)
$ErrorActionPreference = 'Stop'
$buildRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')
Push-Location -LiteralPath $buildRoot
try {
    & (Join-Path $buildRoot '.venv/Scripts/python.exe') scripts/preparar_frontend.py
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar Node local.' }
    $nodeDirectory = (Get-Content -Raw -Encoding UTF8 '.tools/node-path.txt').Trim()
    $env:PATH = "$nodeDirectory;$env:PATH"
    & (Join-Path $nodeDirectory 'npm.cmd') ci --prefix apps/web
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao instalar bibliotecas da interface.' }
    & (Join-Path $nodeDirectory 'npm.cmd') run build --prefix apps/web
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar a interface.' }
    # Limpar os artefatos gerados evita empacotar versões antigas do JavaFX
    # junto das dependências atuais após uma mudança de versão.
    & mvn -B -ntp clean package
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o Java.' }
    if ($Empacotar) {
        & (Join-Path $buildRoot '.venv/Scripts/python.exe') scripts/empacotar_desktop.py
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o programa Windows.' }
    }
    Write-Host 'Compilação concluída.'
} finally { Pop-Location }
