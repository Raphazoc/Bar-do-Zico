param([switch]$RedeLocal,[switch]$SemJanela,[switch]$Treinamento)
$ErrorActionPreference = 'Stop'
$barRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'ambiente.ps1')
Push-Location -LiteralPath $barRoot
try {
    # Abrir a janela deve manter o último modo do ambiente, inclusive HTTPS.
    # Só aceitar o endereço local canônico do ambiente correspondente.
    if (!$RedeLocal) {
        $barRuntimeName = if ($Treinamento) { 'training-runtime.json' } else { 'api-runtime.json' }
        $barExpectedMode = if ($Treinamento) { 'TREINAMENTO' } else { 'OPERACAO' }
        $barExpectedUrl = if ($Treinamento) { 'https://127.0.0.1:18443' } else { 'https://127.0.0.1:8443' }
        $barRuntimePath = Join-Path $barRoot ('.tools/' + $barRuntimeName)
        if (Test-Path -LiteralPath $barRuntimePath) {
            try {
                $barPreviousRuntime = Get-Content -LiteralPath $barRuntimePath -Raw -Encoding UTF8 | ConvertFrom-Json -ErrorAction Stop
                if ($barPreviousRuntime.mode -ceq $barExpectedMode -and $barPreviousRuntime.url -ceq $barExpectedUrl) { $RedeLocal = $true }
            } catch { }
        }
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/pos-reinicio.ps1
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o banco.' }
    # Conceder acesso às tabelas novas sem trocar a senha existente de bar_app.
    $barSavedPassword = [Environment]::GetEnvironmentVariable('BAR_APP_PASSWORD','Process')
    try {
        $env:BAR_APP_PASSWORD = [IO.File]::ReadAllText((Join-Path $barRoot '.tools/docker-app-password.txt')).Trim()
        Get-Content -Raw (Join-Path $barRoot 'infra/postgres/runtime-role.sql') | docker compose exec -T -e BAR_APP_PASSWORD db psql -U bar_migrator -d gestao_bar
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conferir o acesso da aplicação.' }
    } finally { [Environment]::SetEnvironmentVariable('BAR_APP_PASSWORD',$barSavedPassword,'Process') }
    $barArguments = @('scripts/executar_bar.py')
    if ($Treinamento) { $barArguments += '--treinamento' }
    if ($RedeLocal) { $barArguments += '--rede-local' }
    if ($SemJanela) { $barArguments += '--sem-janela' }
    & (Join-Path $barRoot '.venv/Scripts/python.exe') @barArguments
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o sistema.' }
} finally { Pop-Location }
