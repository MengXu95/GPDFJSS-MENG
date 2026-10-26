param(
    [Parameter(Mandatory = $true)][string]$JdkHome,
    [Parameter(Mandatory = $true)][string]$BuildDirectory
)

$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..\..\..')).Path
$source = Join-Path $project 'src'
$fuzzy = Split-Path -Parent $PSScriptRoot
$output = [IO.Path]::GetFullPath($BuildDirectory)
$javac = Join-Path $JdkHome 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) {
    throw "javac is missing from $JdkHome"
}
foreach ($name in @('src', 'bin', 'out', '.git')) {
    $protected = [IO.Path]::GetFullPath((Join-Path $project $name)).TrimEnd('\')
    if ($output.Equals($protected, [StringComparison]::OrdinalIgnoreCase) -or
        $output.StartsWith($protected + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "BuildDirectory must not be inside the existing $name directory."
    }
}
if (Test-Path -LiteralPath $output) {
    throw "Refusing to overwrite an existing build directory: $output"
}
New-Item -ItemType Directory -Path $output | Out-Null
$jars = @(Get-ChildItem -LiteralPath (Join-Path $project 'libraries') -File -Filter '*.jar' |
    Where-Object { $_.Name -notmatch 'javadoc' } | ForEach-Object FullName)
$classpath = (@((Join-Path $project 'bin')) + $jars) -join ';'
$sources = @(Get-ChildItem -LiteralPath $fuzzy -File -Recurse -Filter '*.java' |
    ForEach-Object FullName)
& $javac '-J-Duser.language=en' '-J-Duser.country=US' -encoding UTF-8 -proc:none `
    -Xprefer:source -sourcepath $source -classpath $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) {
    throw "Revision compilation failed (exit $LASTEXITCODE). Original bin/out were not changed."
}
$runtimeClasspath = (@($output, (Join-Path $project 'bin')) + $jars) -join ';'
$runtimeClasspath | Set-Content -LiteralPath (Join-Path $output 'runtime-classpath.txt') -Encoding UTF8
Write-Output "Revision compilation succeeded: $output"
Write-Output 'Checks entry: mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionChecks'
Write-Output 'Experiment entry: mengxu.algorithm.MAPElites.FuzzySystem.revision.RevisionExperiment'
