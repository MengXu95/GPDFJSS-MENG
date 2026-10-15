param(
    [Parameter(Mandatory = $true)][string]$SummaryDirectory,
    [Parameter(Mandatory = $true)][string]$PublishedContext,
    [Parameter(Mandatory = $true)][string]$ReferencePdf
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Format-MeanStd([double]$Mean, [double]$Std, [int]$Digits) {
    if (-not [double]::IsFinite($Mean) -or -not [double]::IsFinite($Std) -or $Mean -lt 0 -or $Std -lt 0) {
        throw 'Table means and standard deviations must be finite and nonnegative.'
    }
    $culture = [Globalization.CultureInfo]::InvariantCulture
    return $Mean.ToString("F$Digits", $culture) + '(' + $Std.ToString("F$Digits", $culture) + ')'
}

$root = (Resolve-Path -LiteralPath $SummaryDirectory).Path
$context = (Resolve-Path -LiteralPath $PublishedContext).Path
$reference = (Resolve-Path -LiteralPath $ReferencePdf).Path
$metricsPath = Join-Path $root 'mean-std.csv'
$validityPath = Join-Path $root 'validity-summary.csv'
$metrics = @(Import-Csv -LiteralPath $metricsPath)
$validity = @(Import-Csv -LiteralPath $validityPath)
$published = @(Import-Csv -LiteralPath $context)
if ($metrics.Count -ne 18 -or $validity.Count -ne 18 -or $published.Count -ne 30) {
    throw 'Expected 18 group/task rows and 30 published scenario/metric entries.'
}
$baseline = @{}
$tables = @{ HV = 'IV'; IGD = 'V'; GD = 'VI' }
foreach ($row in $published) {
    $key = "$($row.Scenario)/$($row.Metric)"
    if ($baseline.ContainsKey($key) -or $row.SourceRuns -ne '30' -or
        -not $tables.ContainsKey($row.Metric) -or $row.SourceTable -ne $tables[$row.Metric] -or
        [int]$row.Scenario -lt 1 -or [int]$row.Scenario -gt 10) {
        throw "Invalid published reference entry: $key"
    }
    $baseline[$key] = Format-MeanStd $row.'PSLGP-I-Mean' $row.'PSLGP-I-Std' 3
}
$validityByTarget = @{}
foreach ($row in $validity) {
    $key = "$($row.Group)/$($row.Scenario)"
    if ($validityByTarget.ContainsKey($key) -or $row.Runs -ne '30' -or $row.PreferenceRows -ne '6000' -or
        [int]$row.ValidPreferences + [int]$row.InvalidPreferences -ne 6000) {
        throw "Invalid validity summary: $key"
    }
    $validityByTarget[$key] = $row
}
$qualityRows = [Collections.Generic.List[object]]::new()
$behaviourRows = [Collections.Generic.List[object]]::new()
$qualityTex = [Text.StringBuilder]::new()
$behaviourTex = [Text.StringBuilder]::new()
$previousGroup = ''
$seen = @{}
foreach ($row in $metrics) {
    $key = "$($row.Group)/$($row.Scenario)"
    if ($seen.ContainsKey($key) -or -not $validityByTarget.ContainsKey($key) -or $row.Runs -ne '30') {
        throw "Invalid metric summary: $key"
    }
    $seen[$key] = $true
    $v = $validityByTarget[$key]
    if ($previousGroup -and $previousGroup -ne $row.Group) {
        [void]$qualityTex.AppendLine('\midrule')
        [void]$behaviourTex.AppendLine('\midrule')
    }
    $quality = [ordered]@{ Group = $row.Group; Scenario = $row.Scenario; Runs = 30 }
    $cells = [Collections.Generic.List[string]]::new()
    $cells.Add($row.Group); $cells.Add($row.Scenario)
    foreach ($metric in 'HV', 'IGD', 'GD') {
        $baselineKey = "$($row.Scenario)/$metric"
        if (-not $baseline.ContainsKey($baselineKey)) { throw "Missing published result: $baselineKey" }
        $local = Format-MeanStd $row."${metric}LocalMean" $row."${metric}LocalStd" 3
        $quality["MPSLGP_${metric}Local"] = $local
        $quality["PSLGP_I_$metric"] = $baseline[$baselineKey]
        $cells.Add($local); $cells.Add($baseline[$baselineKey])
    }
    $qualityRows.Add([pscustomobject]$quality)
    [void]$qualityTex.AppendLine(($cells -join ' & ') + ' \\')
    $behaviour = [ordered]@{ Group = $row.Group; Scenario = $row.Scenario; Runs = 30 }
    $responseCells = [Collections.Generic.List[string]]::new()
    $responseCells.Add("$($row.Group) / $($row.Scenario)")
    foreach ($metric in 'UniqueOutcomes', 'NondominatedOutcomes', 'SeqNodes', 'RoutingNodes') {
        $value = Format-MeanStd $row."${metric}Mean" $row."${metric}Std" 1
        $behaviour[$metric] = $value
        $responseCells.Add($value)
    }
    $behaviour['SingleValidOutcomeRuns'] = $v.SingleValidOutcomeRuns
    $behaviour['InvalidPreferences'] = $v.InvalidPreferences
    $behaviour['PreferenceRows'] = $v.PreferenceRows
    $responseCells.Add($v.SingleValidOutcomeRuns); $responseCells.Add($v.InvalidPreferences)
    $behaviourRows.Add([pscustomobject]$behaviour)
    [void]$behaviourTex.AppendLine(($responseCells -join ' & ') + ' \\')
    $previousGroup = $row.Group
}
$outputs = 'table-vii-comparison.csv', 'table-viii-full.csv', 'table-vii-rows.tex', 'table-viii-rows.tex', 'comparison-inputs.csv'
foreach ($name in $outputs) {
    if (Test-Path -LiteralPath (Join-Path $root $name)) { throw "Preserve existing table output: $name" }
}
$qualityRows | Export-Csv -LiteralPath (Join-Path $root $outputs[0]) -NoTypeInformation -Encoding utf8
$behaviourRows | Export-Csv -LiteralPath (Join-Path $root $outputs[1]) -NoTypeInformation -Encoding utf8
[IO.File]::WriteAllText((Join-Path $root $outputs[2]),
    '\newcommand{\MPSLGPQualityRows}{%' + [Environment]::NewLine + $qualityTex.ToString() + '}' + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $root $outputs[3]),
    '\newcommand{\MPSLGPBehaviourRows}{%' + [Environment]::NewLine + $behaviourTex.ToString() + '}' + [Environment]::NewLine,
    [Text.UTF8Encoding]::new($false))
$sources = foreach ($path in @($metricsPath, $validityPath, (Join-Path $root 'per-run.csv'),
        (Join-Path $root 'protocol.properties'), $context, $reference, $PSCommandPath)) {
    [pscustomobject]@{ Path = $path; SHA256 = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
}
$sources | Export-Csv -LiteralPath (Join-Path $root $outputs[4]) -NoTypeInformation -Encoding utf8
Write-Output "Saved 18 quality and behaviour rows in $root; published values are contextual, not recalibrated."
