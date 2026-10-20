$ErrorActionPreference = 'Stop'
$dockerRoot = Split-Path -Parent $PSScriptRoot
$dockerInstalledDesktop = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/Docker Desktop.exe'
$dockerInstalledCli = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
if ((Test-Path -LiteralPath $dockerInstalledDesktop) -and (Test-Path -LiteralPath $dockerInstalledCli)) {
    & $dockerInstalledCli --version
    if ($LASTEXITCODE -eq 0) {
        Write-Output 'Docker Desktop já está instalado; instalação preservada.'
        return
    }
}
$dockerInstaller = Join-Path $dockerRoot '.tools/downloads/DockerDesktopInstaller.exe'
if (!(Test-Path -LiteralPath $dockerInstaller)) {
    throw 'Instalador não encontrado. Consulte docs/AMBIENTE.md para o download oficial.'
}
$dockerExpected = '73431db38491f770543880366228a23c993f84d1e8e045f56d83b8a98940cf76'
if ((Get-FileHash -LiteralPath $dockerInstaller -Algorithm SHA256).Hash.ToLowerInvariant() -ne $dockerExpected) {
    throw 'Checksum do instalador diferente do esperado.'
}
if ((Get-AuthenticodeSignature -LiteralPath $dockerInstaller).Status -ne 'Valid') {
    throw 'Assinatura digital do instalador inválida.'
}
$dockerAttemptStarted = [DateTime]::UtcNow
$dockerProcess = Start-Process -FilePath $dockerInstaller -ArgumentList @(
    'install', '--user', '--quiet', '--accept-license', '--backend=wsl-2'
) -WindowStyle Hidden -Wait -PassThru
if ($dockerProcess.ExitCode -ne 0) {
    $dockerLog = Join-Path $env:LOCALAPPDATA 'Docker/install-log.txt'
    # O bootstrapper pode devolver código diferente de zero após o instalador
    # interno confirmar sucesso. Conferir o resultado concreto antes de falhar.
    if ((Test-Path -LiteralPath $dockerInstalledDesktop) -and
        (Test-Path -LiteralPath $dockerInstalledCli) -and
        (Test-Path -LiteralPath $dockerLog) -and
        ((Get-Item -LiteralPath $dockerLog).LastWriteTimeUtc -ge $dockerAttemptStarted) -and
        (Select-String -LiteralPath $dockerLog -Pattern 'Installation succeeded' -Quiet)) {
        & $dockerInstalledCli --version
        if ($LASTEXITCODE -eq 0) {
            Write-Output "Docker instalado e CLI confirmado (código do bootstrapper: $($dockerProcess.ExitCode))."
            return
        }
    }
    throw "Docker não instalado (código $($dockerProcess.ExitCode)). Consulte $env:LOCALAPPDATA/Docker/install-log.txt. Verifique os pré-requisitos em docs/AMBIENTE.md."
}
Write-Output 'Docker Desktop instalado. Abra-o pelo menu Iniciar para iniciar o engine.'
