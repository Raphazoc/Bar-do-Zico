param([switch]$SomenteValidar)

$ErrorActionPreference = 'Stop'
$resumeRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')
$resumePython = Join-Path $resumeRoot '.venv/Scripts/python.exe'
$resumeCompose = Join-Path $resumeRoot '.tools/compose/docker-compose.exe'

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Comando falhou: $Executable $($Arguments -join ' ')" }
}

Push-Location -LiteralPath $resumeRoot
try {
    Invoke-Checked -Executable $resumePython -Arguments @((Join-Path $PSScriptRoot 'preparar_env.py'))
    # Validação estática com credenciais resolvidas, sem imprimir configuração.
    if (Test-Path -LiteralPath $resumeCompose) {
        Invoke-Checked -Executable $resumeCompose -Arguments @('config','--quiet')
        Write-Output 'Configuração Compose validada.'
    }
    if ($SomenteValidar) {
        if (!(Test-Path -LiteralPath $resumeCompose)) { throw 'Validador Compose local não encontrado.' }
        Write-Output 'Conferência offline concluída; nenhum container foi iniciado.'
        return
    }

    $resumeDesktop = @(
        (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/Docker Desktop.exe'),
        'C:/Program Files/Docker/Docker/Docker Desktop.exe'
    ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (!$resumeDesktop) {
        & (Join-Path $PSScriptRoot 'instalar-docker.ps1')
        $resumeDesktop = @(
            (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/Docker Desktop.exe'),
            'C:/Program Files/Docker/Docker/Docker Desktop.exe'
        ) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    }
    if (!$resumeDesktop) { throw 'Docker Desktop não encontrado após a instalação.' }
    . (Join-Path $PSScriptRoot 'ambiente.ps1')
    $resumeDockerCommand = Get-Command docker -ErrorAction Stop
    $resumeDocker = $resumeDockerCommand.Source
    if (!(Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue)) {
        Start-Process -FilePath $resumeDesktop -WindowStyle Hidden | Out-Null
    }
    Write-Output 'Aguardando Docker Desktop (até 120 segundos)...'
    $resumeReady = $false
    for ($resumeAttempt = 0; $resumeAttempt -lt 24; $resumeAttempt++) {
        $resumeProbeSucceeded = $false
        try {
            & $resumeDocker info --format '{{.ServerVersion}}' *> $null
            $resumeProbeSucceeded = $LASTEXITCODE -eq 0
        }
        catch { $resumeProbeSucceeded = $false }
        if ($resumeProbeSucceeded) { $resumeReady = $true; break }
        Start-Sleep -Seconds 5
    }
    if (!$resumeReady) {
        throw 'Engine indisponível. Abra Docker Desktop para conferir WSL/virtualização e execute novamente.'
    }
    Invoke-Checked -Executable $resumeDocker -Arguments @('compose','config','--quiet')
    Invoke-Checked -Executable $resumeDocker -Arguments @('compose','up','-d','--wait','db')
    Invoke-Checked -Executable $resumeDocker -Arguments @('compose','run','--rm','migrate')
    Invoke-Checked -Executable $resumeDocker -Arguments @('compose','run','--rm','migrate','validate')
    Write-Output 'Banco Docker pronto em 127.0.0.1:5432 / gestao_bar, schema bar.'
}
finally { Pop-Location }
