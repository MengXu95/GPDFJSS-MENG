param(
    [string]$Source = (Join-Path $PSScriptRoot 'DFJSS_Single_to_Multitask.pptx')
)

$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$output = Join-Path (Split-Path -Parent $sourcePath) ([System.IO.Path]::GetFileNameWithoutExtension($sourcePath))
$powerpoint = New-Object -ComObject PowerPoint.Application
$presentation = $null
try {
    $presentation = $powerpoint.Presentations.Open($sourcePath, -1, 0, 0)
    if ($presentation.Slides.Count -ne 1) { throw 'Expected one slide.' }
    $slide = $presentation.Slides.Item([int]1)
    $overflow = @()
    for ($index = 1; $index -le $slide.Shapes.Count; $index++) {
        $shape = $slide.Shapes.Item([int]$index)
        if ($shape.HasTextFrame -eq -1 -and $shape.TextFrame.HasText -eq -1) {
            $range = $shape.TextFrame2.TextRange
            $usableWidth = $shape.Width - $shape.TextFrame2.MarginLeft - $shape.TextFrame2.MarginRight
            $usableHeight = $shape.Height - $shape.TextFrame2.MarginTop - $shape.TextFrame2.MarginBottom
            if ($range.BoundWidth -gt $usableWidth + 1 -or $range.BoundHeight -gt $usableHeight + 1) {
                $overflow += "$($shape.Name): $([math]::Round($range.BoundWidth,1))x$([math]::Round($range.BoundHeight,1)) vs $([math]::Round($usableWidth,1))x$([math]::Round($usableHeight,1))"
            }
        }
    }
    if ($overflow.Count) { throw ('Text overflow: ' + ($overflow -join '; ')) }
    $presentation.SaveAs($output + '.pdf', 32)
    $slide.Export($output + '.png', 'PNG', 2400, 1155)
    Write-Output 'PASS: actual PowerPoint text bounds fit; PDF and PNG exported.'
}
finally {
    if ($null -ne $presentation) {
        $presentation.Close()
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($presentation)
    }
    if ($powerpoint.Presentations.Count -eq 0) { $powerpoint.Quit() }
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($powerpoint)
}