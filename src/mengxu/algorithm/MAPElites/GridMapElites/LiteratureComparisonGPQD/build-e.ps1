param(
    [Parameter(Mandatory = $true)][string]$JdkHome,
    [Parameter(Mandatory = $true)][string]$BuildDirectory
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..\..\..')).Path
$output = [IO.Path]::GetFullPath($BuildDirectory)
$javac = Join-Path $JdkHome 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { throw "Missing javac: $javac" }
foreach ($name in @('src','bin','out','.git')) {
    $protected = [IO.Path]::GetFullPath((Join-Path $project $name)).TrimEnd('\')
    if ($output.Equals($protected,[StringComparison]::OrdinalIgnoreCase) -or
        $output.StartsWith($protected + '\',[StringComparison]::OrdinalIgnoreCase)) {
        throw "Build output cannot be inside $name."
    }
}
if (Test-Path -LiteralPath $output) { throw "Refusing existing build directory: $output" }
New-Item -ItemType Directory -Path $output | Out-Null
$jars = @(Get-ChildItem -LiteralPath (Join-Path $project 'libraries') -File -Filter '*.jar' |
    Where-Object Name -NotMatch 'javadoc' | ForEach-Object FullName)
$classpath = (@((Join-Path $project 'bin')) + $jars) -join ';'
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -File -Filter '*.java' | ForEach-Object FullName)
$sources += Join-Path $project 'src\mengxu\algorithm\MAPElites\GridMapElites\LexicaseGPQD\LexicaseBaselineChecks.java'
& $javac '-J-Duser.language=en' '-J-Duser.country=US' -encoding UTF8 -proc:none `
    -Xprefer:source -sourcepath (Join-Path $project 'src') -classpath $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'E compilation failed; original bin/out were not changed.' }
(@($output,(Join-Path $project 'bin')) + $jars) -join ';' |
    Set-Content -LiteralPath (Join-Path $output 'runtime-classpath.txt') -Encoding UTF8
Write-Output "Literature-comparison workflow compiled: $output"
