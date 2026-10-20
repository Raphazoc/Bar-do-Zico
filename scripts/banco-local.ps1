param(
    [ValidateSet('Iniciar', 'Parar', 'Status', 'Migrar', 'Testar')]
    [string]$Acao = 'Status'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ambiente.ps1')
$databaseRoot = Split-Path -Parent $PSScriptRoot
$databaseData = Join-Path $databaseRoot '.tools/pgdata'
$databaseControl = Join-Path $databaseRoot '.tools/pgsql/bin/pg_ctl.exe'
$databaseFlyway = Join-Path $databaseRoot '.tools/flyway-13.10.3/flyway.cmd'
$databasePython = Join-Path $databaseRoot '.venv/Scripts/python.exe'

if (!(Test-Path -LiteralPath $databaseControl) -or !(Test-Path -LiteralPath $databaseData)) {
    throw 'PostgreSQL local não encontrado. Use o ambiente Docker descrito no README.'
}

function Test-DatabaseRunning {
    & $databaseControl -D $databaseData status *> $null
    return $LASTEXITCODE -eq 0
}

function Start-LocalDatabase {
    if (!(Test-DatabaseRunning)) {
        & $databaseControl -D $databaseData -l (Join-Path $databaseRoot '.tools/postgres.log') start -w
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar PostgreSQL local.' }
    }
}

function Stop-LocalDatabase {
    if (Test-DatabaseRunning) {
        & $databaseControl -D $databaseData stop -m fast -w
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao parar PostgreSQL local.' }
    }
}

switch ($Acao) {
    'Status' {
        if (Test-DatabaseRunning) { Write-Output 'PostgreSQL local ativo: 127.0.0.1:55432 / gestao_bar' }
        else { Write-Output 'PostgreSQL local parado.' }
    }
    'Iniciar' { Start-LocalDatabase }
    'Parar' { Stop-LocalDatabase }
    { $_ -in 'Migrar', 'Testar' } {
        $databasePasswordFile = Join-Path $databaseRoot '.tools/pg-password.txt'
        if (!(Test-Path -LiteralPath $databasePasswordFile)) { throw 'Credencial local ausente.' }
        $databaseEnvNames = @('PGHOST','PGPORT','PGUSER','PGPASSWORD','PGDATABASE',
            'FLYWAY_URL','FLYWAY_USER','FLYWAY_PASSWORD','FLYWAY_SCHEMAS','FLYWAY_DEFAULT_SCHEMA',
            'FLYWAY_CLEAN_DISABLED','FLYWAY_LOCATIONS')
        $databaseSaved = @{}
        foreach ($databaseName in $databaseEnvNames) {
            $databaseSaved[$databaseName] = [Environment]::GetEnvironmentVariable($databaseName, 'Process')
        }
        $databaseStartedHere = !(Test-DatabaseRunning)
        try {
            Start-LocalDatabase
            $env:PGHOST = '127.0.0.1'
            $env:PGPORT = '55432'
            $env:PGUSER = 'bar_test'
            $env:PGPASSWORD = [IO.File]::ReadAllText($databasePasswordFile).Trim()
            $env:PGDATABASE = 'gestao_bar'
            if ($Acao -eq 'Migrar') {
                $env:FLYWAY_URL = 'jdbc:postgresql://127.0.0.1:55432/gestao_bar'
                $env:FLYWAY_USER = $env:PGUSER
                $env:FLYWAY_PASSWORD = $env:PGPASSWORD
                $env:FLYWAY_SCHEMAS = 'bar'
                $env:FLYWAY_DEFAULT_SCHEMA = 'bar'
                $env:FLYWAY_CLEAN_DISABLED = 'true'
                $env:FLYWAY_LOCATIONS = 'filesystem:' + ((Join-Path $databaseRoot 'apps/api/src/main/resources/db/migration') -replace '\\', '/')
                & $databaseFlyway migrate
            }
            else {
                & $databasePython (Join-Path $databaseRoot 'scripts/validate_database.py') --flyway $databaseFlyway
            }
            if ($LASTEXITCODE -ne 0) { throw "Falha na ação $Acao." }
        }
        finally {
            foreach ($databaseName in $databaseEnvNames) {
                [Environment]::SetEnvironmentVariable($databaseName, $databaseSaved[$databaseName], 'Process')
            }
            if ($Acao -eq 'Testar' -and $databaseStartedHere) { Stop-LocalDatabase }
        }
    }
}
