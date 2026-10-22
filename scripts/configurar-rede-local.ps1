[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [int]$InterfaceIndex = 0,
    [string]$ExpectedInterfaceGuid = ''
)
$ErrorActionPreference = 'Stop'
$networkRoot = Split-Path -Parent $PSScriptRoot
$networkDirectory = Join-Path $networkRoot '.tools/network'
$networkGroup = 'Bar do Zico - rede local'
$networkCreatedRules = @()
$networkBeforeCategory = $null
$networkStatePath = Join-Path $networkDirectory 'result.json'

try {
    if (!$InterfaceIndex) {
        $networkCandidates = @(Get-NetIPConfiguration | Where-Object {
            $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' -and $_.NetAdapter.HardwareInterface
        })
        if ($networkCandidates.Count -ne 1) {
            throw 'Não foi possível escolher uma única conexão residencial. Execute ferramentas/Verificar Rede.cmd e informe -InterfaceIndex ao configurar.'
        }
        $InterfaceIndex = $networkCandidates[0].InterfaceIndex
    }
    $networkAdapter = Get-NetAdapter -InterfaceIndex $InterfaceIndex -ErrorAction Stop
    if (($ExpectedInterfaceGuid -and [guid]$networkAdapter.InterfaceGuid -ne [guid]$ExpectedInterfaceGuid) -or $networkAdapter.Status -ne 'Up') {
        throw 'A conexão mudou. Confira a interface antes de aplicar a configuração.'
    }
    $networkProfile = Get-NetConnectionProfile -InterfaceIndex $InterfaceIndex -ErrorAction Stop
    if ($networkProfile.NetworkCategory -eq 'DomainAuthenticated') { throw 'Rede gerenciada por domínio: configuração preservada.' }
    $networkBeforeCategory = [string]$networkProfile.NetworkCategory
    $networkRuleProfile = $networkBeforeCategory
    . (Join-Path $PSScriptRoot 'ambiente.ps1')
    $networkJavaHome = $env:JAVA_HOME
    if (!$networkJavaHome) { throw 'JDK 25 do projeto não foi encontrado.' }
    $networkJava = (Resolve-Path -LiteralPath (Join-Path $networkJavaHome 'bin/java.exe')).Path
    $networkNames = @('BarDoZico-Local-8443','BarDoZico-Local-18443')
    foreach ($networkName in $networkNames) {
        $networkExisting = Get-NetFirewallRule -Name $networkName -ErrorAction SilentlyContinue
        if ($networkExisting) {
            $networkPort = $networkExisting | Get-NetFirewallPortFilter
            $networkAddress = $networkExisting | Get-NetFirewallAddressFilter
            $networkApplication = $networkExisting | Get-NetFirewallApplicationFilter
            $networkInterface = $networkExisting | Get-NetFirewallInterfaceFilter
            $networkExpectedPort = $networkName.Split('-')[-1]
            if ($networkExisting.Group -ne $networkGroup -or [string]$networkExisting.Profile -ne $networkRuleProfile -or
                [string]$networkExisting.Direction -ne 'Inbound' -or [string]$networkExisting.Action -ne 'Allow' -or
                [string]$networkExisting.Enabled -ne 'True' -or [string]$networkPort.Protocol -ne 'TCP' -or
                [string]$networkPort.LocalPort -ne $networkExpectedPort -or
                ($networkAddress.RemoteAddress -join ',') -ne 'LocalSubnet' -or
                $networkApplication.Program -ne $networkJava -or
                ($networkInterface.InterfaceAlias -join ',') -ne $networkAdapter.InterfaceAlias) {
                throw "A regra $networkName possui configuração diferente. Nenhuma regra existente será sobrescrita."
            }
        }
    }
    $networkDescription = 'Preservar perfil ' + $networkBeforeCategory + ' em ' + $networkAdapter.InterfaceAlias + '; entrada TCP 8443/18443 para Java, somente sub-rede local nesta interface'
    if (!$PSCmdlet.ShouldProcess($networkDescription,'Configurar acesso local do Bar do Zico')) { return }
    $networkPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
    if (!$networkPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'O Windows exige executar esta configuração como administrador.'
    }
    New-Item -ItemType Directory -Path $networkDirectory -Force | Out-Null
    $networkBackupPath = Join-Path $networkDirectory 'before.json'
    if (!(Test-Path -LiteralPath $networkBackupPath)) {
        [ordered]@{
            interfaceIndex = $InterfaceIndex
            interfaceGuid = [string]$networkAdapter.InterfaceGuid
            interfaceAlias = $networkAdapter.InterfaceAlias
            originalCategory = $networkBeforeCategory
            existingRules = @($networkNames | Where-Object { Get-NetFirewallRule -Name $_ -ErrorAction SilentlyContinue })
            recordedAt = (Get-Date).ToString('o')
        } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $networkBackupPath -Encoding UTF8
    }
    foreach ($networkPortNumber in @(8443,18443)) {
        $networkName = 'BarDoZico-Local-' + $networkPortNumber
        if (!(Get-NetFirewallRule -Name $networkName -ErrorAction SilentlyContinue)) {
            New-NetFirewallRule -Name $networkName -DisplayName "Bar do Zico - HTTPS local $networkPortNumber" `
                -Description 'Acesso do Android ao Java na rede residencial; perfil do Windows preservado e origem restrita à sub-rede local.' `
                -Group $networkGroup -Enabled True -Profile $networkRuleProfile -Direction Inbound -Action Allow `
                -Protocol TCP -LocalPort $networkPortNumber -RemoteAddress LocalSubnet `
                -InterfaceAlias $networkAdapter.InterfaceAlias -Program $networkJava -EdgeTraversalPolicy Block | Out-Null
            $networkCreatedRules += $networkName
        }
    }
    if ([string](Get-NetConnectionProfile -InterfaceIndex $InterfaceIndex).NetworkCategory -ne $networkBeforeCategory) {
        throw 'O perfil da conexão mudou durante a configuração.'
    }
    [ordered]@{
        success = $true
        interfaceAlias = $networkAdapter.InterfaceAlias
        interfaceGuid = [string]$networkAdapter.InterfaceGuid
        category = $networkBeforeCategory
        ports = @(8443,18443)
        remoteAddress = 'LocalSubnet'
        program = $networkJava
        completedAt = (Get-Date).ToString('o')
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $networkStatePath -Encoding UTF8
    Write-Output 'Acesso local configurado nas portas 8443 e 18443, preservando o perfil da conexão.'
} catch {
    $networkFailure = $_.Exception.Message
    foreach ($networkName in $networkCreatedRules) { Remove-NetFirewallRule -Name $networkName -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath $networkDirectory) {
        @{ success = $false; error = $networkFailure } | ConvertTo-Json | Set-Content -LiteralPath $networkStatePath -Encoding UTF8
    }
    Write-Error $networkFailure
    exit 1
}
