param(
    [ValidateSet('build', 'train', 'analyze', 'test')]
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
$buildDirectory = Join-Path $repoRoot 'out/evospeakv1-classes'
$classPath = "$buildDirectory;$(Join-Path $repoRoot 'libraries/*')"
$configPath = (Resolve-Path -LiteralPath $ParamsFile).Path

Push-Location $repoRoot
try {
    $sources = @((Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java').FullName)
    $sources += Join-Path $repoRoot 'test/mengxu/algorithm/EvoSpeakV1/EvoSpeakV1RegressionTest.java'
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
        throw 'EvoSpeakV1 compilation failed.'
    }
    if ($Action -eq 'build') {
        Write-Output "EvoSpeakV1 classes: $buildDirectory"
        return
    }
    $mainClass = switch ($Action) {
        'train' { 'mengxu.algorithm.EvoSpeakV1.EvoSpeakMain' }
        'analyze' { 'mengxu.algorithm.EvoSpeakV1.RuleAnalysisMain' }
        'test' { 'mengxu.algorithm.EvoSpeakV1.EvoSpeakV1RegressionTest' }
    }
    $arguments = @('-cp', $classPath, $mainClass)
    if ($Action -ne 'test') {
        $arguments += @('-file', $configPath)
        foreach ($override in $Overrides) {
            $arguments += @('-p', $override)
        }
    }
    $arguments += $ExtraArgs
    & $java @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "EvoSpeakV1 $Action failed. See the error above and the run directory status/validation report."
    }
} finally {
    Pop-Location
}