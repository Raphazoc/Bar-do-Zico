$ErrorActionPreference = 'Stop'
$validationRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')
$validationNames = @('PGHOST','PGPORT','PGUSER','PGPASSWORD','PGDATABASE')
$validationSaved = @{}
foreach ($validationName in $validationNames) {
    $validationSaved[$validationName] = [Environment]::GetEnvironmentVariable($validationName, 'Process')
}

Push-Location -LiteralPath $validationRoot
try {
    # Capturar em memória: não exibir o JSON, que contém a credencial resolvida.
    $validationJson = & docker compose config --format json
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao carregar a configuração do Compose.' }
    $validationConfig = ($validationJson -join "`n") | ConvertFrom-Json
    $validationDb = $validationConfig.services.db
    $validationPort = $validationDb.ports | Where-Object { $_.target -eq 5432 } | Select-Object -First 1
    if (!$validationPort -or $validationPort.host_ip -ne '127.0.0.1') {
        throw 'Validação automática exige PostgreSQL publicado somente em 127.0.0.1.'
    }
    $env:PGHOST = '127.0.0.1'
    $env:PGPORT = [string]$validationPort.published
    $env:PGUSER = $validationDb.environment.POSTGRES_USER
    $env:PGPASSWORD = $validationDb.environment.POSTGRES_PASSWORD
    $env:PGDATABASE = $validationDb.environment.POSTGRES_DB
    & (Join-Path $validationRoot '.venv/Scripts/python.exe') `
        (Join-Path $PSScriptRoot 'validate_database.py') `
        --flyway (Join-Path $validationRoot '.tools/flyway-13.10.3/flyway.cmd')
    if ($LASTEXITCODE -ne 0) { throw 'A validação do PostgreSQL Docker falhou.' }
}
finally {
    foreach ($validationName in $validationNames) {
        [Environment]::SetEnvironmentVariable($validationName, $validationSaved[$validationName], 'Process')
    }
    Pop-Location
}
