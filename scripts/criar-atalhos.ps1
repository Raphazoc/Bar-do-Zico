$ErrorActionPreference = 'Stop'
$shortcutRoot = Split-Path -Parent $PSScriptRoot
$shortcutDesktop = [Environment]::GetFolderPath('Desktop')
$shortcutIcon = Join-Path $shortcutRoot 'output/app/Bar do Zico/Bar do Zico.exe'
if (!(Test-Path -LiteralPath $shortcutIcon -PathType Leaf)) { throw 'O programa ainda precisa ser gerado com scripts/compilar.ps1 -Empacotar.' }
if (!(Test-Path -LiteralPath $shortcutDesktop -PathType Container)) { throw 'A pasta da Área de Trabalho não foi encontrada.' }
$shortcutShell = New-Object -ComObject WScript.Shell
$shortcutDefinitions = @(
    @{ Name = 'Bar do Zico'; File = 'Iniciar Bar.cmd'; Icon = 'bar-do-zico.ico'; Description = 'Abrir o sistema do Bar do Zico, com o banco local.' },
    @{ Name = 'Bar do Zico - Treinamento'; File = 'Iniciar Treinamento.cmd'; Icon = 'bar-do-zico-treinamento.ico'; Description = 'Abrir o treinamento com mesas e produtos fictícios em banco separado.' }
)
try {
    foreach ($shortcutDefinition in $shortcutDefinitions) {
        $shortcutTarget = (Resolve-Path -LiteralPath (Join-Path $shortcutRoot $shortcutDefinition.File)).Path
        $shortcutPath = Join-Path $shortcutDesktop ($shortcutDefinition.Name + '.lnk')
        $shortcutLink = $shortcutShell.CreateShortcut($shortcutPath)
        if ((Test-Path -LiteralPath $shortcutPath) -and ($shortcutLink.TargetPath -ne $shortcutTarget -or $shortcutLink.WorkingDirectory -ne $shortcutRoot)) {
            throw ('O atalho existente tem outro destino e foi preservado: ' + $shortcutPath)
        }
        $shortcutLink.TargetPath = $shortcutTarget
        $shortcutLink.WorkingDirectory = $shortcutRoot
        $shortcutCustomIcon = Join-Path $shortcutRoot ('assets/icons/' + $shortcutDefinition.Icon)
        if (!(Test-Path -LiteralPath $shortcutCustomIcon -PathType Leaf)) { throw ('Ícone não encontrado: ' + $shortcutCustomIcon) }
        $shortcutLink.IconLocation = $shortcutCustomIcon + ',0'
        $shortcutLink.Description = $shortcutDefinition.Description
        $shortcutLink.WindowStyle = 7
        $shortcutLink.Save()
        # Conferir o destino efetivamente salvo antes de anunciar sucesso.
        $shortcutSaved = $shortcutShell.CreateShortcut($shortcutPath)
        if ($shortcutSaved.TargetPath -ne $shortcutTarget -or $shortcutSaved.WorkingDirectory -ne $shortcutRoot -or $shortcutSaved.IconLocation -ne ($shortcutCustomIcon + ',0')) { throw ('Não foi possível conferir o atalho: ' + $shortcutPath) }
        Write-Output ('Atalho pronto na Área de Trabalho: ' + $shortcutDefinition.Name)
    }
} finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($shortcutShell) }
