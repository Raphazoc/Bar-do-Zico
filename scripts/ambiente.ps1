# Carrega as ferramentas instaladas para este projeto na sessão atual.
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJavaMarker = Join-Path $taskRoot '.tools/java-home.txt'
if (Test-Path -LiteralPath $taskJavaMarker) {
    $taskJavaHome = (Get-Content -LiteralPath $taskJavaMarker -Raw -Encoding UTF8).Trim()
    if (!$taskJavaHome) { throw 'O marcador .tools/java-home.txt está vazio.' }
    if (![System.IO.Path]::IsPathRooted($taskJavaHome)) { $taskJavaHome = Join-Path $taskRoot $taskJavaHome }
} elseif (Test-Path -LiteralPath (Join-Path $taskRoot '.tools/jdk-25/bin/java.exe')) {
    $taskJavaHome = Join-Path $taskRoot '.tools/jdk-25'
} else {
    $taskJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    if (!$taskJavaHome) { $taskJavaHome = $env:JAVA_HOME }
    if (!$taskJavaHome) {
        $taskJavaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($taskJavaCommand) { $taskJavaHome = Split-Path -Parent (Split-Path -Parent $taskJavaCommand.Source) }
    }
}
if (!$taskJavaHome) { throw 'JDK 25 não encontrado. Informe sua pasta em .tools/java-home.txt.' }
$taskMavenHome = [Environment]::GetEnvironmentVariable('MAVEN_HOME', 'User')
$taskPaths = @()

if ($taskJavaHome) {
    foreach ($taskJavaTool in @('java','javac','jpackage')) {
        if (!(Test-Path -LiteralPath (Join-Path $taskJavaHome "bin/$taskJavaTool.exe"))) { throw "JDK incompleto: $taskJavaTool não encontrado em $taskJavaHome." }
    }
    $taskJavaRelease = Join-Path $taskJavaHome 'release'
    if (!(Test-Path -LiteralPath $taskJavaRelease) -or !((Get-Content -LiteralPath $taskJavaRelease) -match '^JAVA_VERSION="25[."]')) {
        throw 'Este projeto usa JDK 25. Informe sua pasta em .tools/java-home.txt; o Java do Windows será preservado.'
    }
    $env:JAVA_HOME = $taskJavaHome
    $taskPaths += Join-Path $taskJavaHome 'bin'
}
if ($taskMavenHome -and (Test-Path -LiteralPath (Join-Path $taskMavenHome 'bin/mvn.cmd'))) {
    $env:MAVEN_HOME = $taskMavenHome
    $taskPaths += Join-Path $taskMavenHome 'bin'
}
$taskVenv = Join-Path $taskRoot '.venv'
if (Test-Path -LiteralPath (Join-Path $taskVenv 'Scripts/python.exe')) {
    $env:VIRTUAL_ENV = $taskVenv
    $taskPaths += Join-Path $taskVenv 'Scripts'
}
foreach ($taskRelative in @('.tools/pgsql/bin', '.tools/flyway-13.10.3')) {
    $taskDirectory = Join-Path $taskRoot $taskRelative
    if (Test-Path -LiteralPath $taskDirectory) { $taskPaths += $taskDirectory }
}
foreach ($taskDockerPath in @(
    (Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'),
    'C:/Program Files/Docker/Docker/resources/bin'
)) {
    if (Test-Path -LiteralPath $taskDockerPath) { $taskPaths += $taskDockerPath }
}
$env:PATH = (($taskPaths + ($env:PATH -split ';')) | Where-Object { $_ } | Select-Object -Unique) -join ';'
