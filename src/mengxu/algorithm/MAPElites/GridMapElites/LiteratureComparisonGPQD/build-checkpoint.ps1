param(
    [Parameter(Mandatory = $true)][string]$BuildDirectory,
    [string]$JdkHome = 'C:\Users\xumen\.jdks\openjdk-26.0.1'
)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..\..\..\..')).Path
$output = [IO.Path]::GetFullPath($BuildDirectory)
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
$existing = Join-Path $project 'out\production\GPDFJSS-MENG'
$classpath = (@($existing,(Join-Path $project 'bin')) + $jars) -join ';'
$sources = @('ECheckpointAnalysis.java','ECheckpointChecks.java') |
    ForEach-Object { Join-Path $PSScriptRoot $_ }
& (Join-Path $JdkHome 'bin\javac.exe') '-J-Duser.language=en' '-J-Duser.country=US' `
    -encoding UTF8 -proc:none -implicit:none -sourcepath $output -classpath $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Checkpoint compilation failed; existing bin/out were not changed.' }
(@($output,$existing,(Join-Path $project 'bin')) + $jars) -join ';' |
    Set-Content -LiteralPath (Join-Path $output 'runtime-classpath.txt') -Encoding UTF8
Write-Output "Isolated checkpoint postprocessing compiled: $output"
