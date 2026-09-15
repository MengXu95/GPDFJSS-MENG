param(
    [ValidateSet('build', 'train', 'analyze', 'test', 'check', 'auth', 'prompt')]
    [string]$Action = 'train',
    [string]$ParamsFile = (Join-Path $PSScriptRoot 'evospeak.params'),
    [string[]]$Overrides = @(),
    [string[]]$ExtraArgs = @()
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin/javac.exe'))) {
    $compiler = Get-Command javac -ErrorAction SilentlyContinue
    if ($compiler) {
        $jdk = Split-Path (Split-Path $compiler.Source -Parent) -Parent
    } else {
        $jdkRoot = Join-Path $env:USERPROFILE '.jdks'
        $installed = if (Test-Path $jdkRoot) {
            Get-ChildItem -LiteralPath $jdkRoot -Directory |
                Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
                Sort-Object Name -Descending | Select-Object -First 1
        }
        if (-not $installed) {
            throw 'Install JDK 11 or newer and set JAVA_HOME. The project was tested with OpenJDK 26.0.1.'
        }
        $jdk = $installed.FullName
    }
}
$java = Join-Path $jdk 'bin/java.exe'
$javac = Join-Path $jdk 'bin/javac.exe'
$buildDirectory = Join-Path $repoRoot 'out/onlineevospeak-classes'
$classPath = "$buildDirectory;$(Join-Path $repoRoot 'libraries/*')"
$configPath = (Resolve-Path -LiteralPath $ParamsFile).Path

Push-Location $repoRoot
try {
    $sources = @((Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java').FullName)
    $sources += Join-Path $repoRoot 'test/mengxu/algorithm/OnlineEvoSpeak/OnlineEvoSpeakRegressionTest.java'
    $parameters = ConvertFrom-StringData -StringData (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'evospeak.params') -Raw)
    foreach ($value in $parameters.Values) {
        if ($value -match '^(ec|yimei|mengxu)\.[A-Za-z0-9_.]+$') {
            $sourcePath = Join-Path $repoRoot ('src/' + $value.Replace('.', '/') + '.java')
            if (Test-Path -LiteralPath $sourcePath) {
                $sources += $sourcePath
            }
        }
    }
    & $javac --release 11 -encoding UTF-8 -nowarn -cp (Join-Path $repoRoot 'libraries/*') -sourcepath 'src;test' -d $buildDirectory ($sources | Sort-Object -Unique)
    if ($LASTEXITCODE -ne 0) {
        throw 'OnlineEvoSpeak compilation failed.'
    }
    if ($Action -eq 'build') {
        Write-Output "OnlineEvoSpeak classes: $buildDirectory"
        return
    }
    $mainClass = switch ($Action) {
        'train' { 'mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain' }
        'analyze' { 'mengxu.algorithm.OnlineEvoSpeak.RuleAnalysisMain' }
        'test' { 'mengxu.algorithm.OnlineEvoSpeak.OnlineEvoSpeakRegressionTest' }
        'check' { 'mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain' }
        'auth' { 'mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain' }
        'prompt' { 'mengxu.algorithm.OnlineEvoSpeak.EvoSpeakMain' }
    }
    $arguments = @('-cp', $classPath, $mainClass)
    if ($Action -ne 'test') {
        $arguments += @('-file', $configPath)
        foreach ($override in $Overrides) {
            $arguments += @('-p', $override)
        }
    }
    if ($Action -eq 'check') {
        $arguments += '--check-connection'
    }
    if ($Action -eq 'auth') {
        $arguments += '--check-auth'
    }
    if ($Action -eq 'prompt') {
        $arguments += '--export-prompt'
    }
    $arguments += $ExtraArgs
    & $java @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "OnlineEvoSpeak $Action failed. See the error above and the run directory status/validation report."
    }
} finally {
    Pop-Location
}