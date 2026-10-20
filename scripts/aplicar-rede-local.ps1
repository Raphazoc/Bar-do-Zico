$ErrorActionPreference = 'Stop'
$networkSetupScript = Join-Path $PSScriptRoot 'configurar-rede-local.ps1'
$networkSetupShell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
try {
    $networkSetupProcess = Start-Process -FilePath $networkSetupShell -Verb RunAs -WindowStyle Hidden `
        -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"' + $networkSetupScript + '"')) -PassThru
    $networkSetupProcess.WaitForExit()
    if ($networkSetupProcess.ExitCode -ne 0) { throw 'A configuração não foi concluída. Confira .tools/network/result.json.' }
    Write-Output 'Configuração da rede concluída. Abra Treinamento no Android.cmd.'
} catch {
    Write-Error ('Não foi possível aplicar a configuração administrativa: ' + $_.Exception.Message)
    exit 1
}
