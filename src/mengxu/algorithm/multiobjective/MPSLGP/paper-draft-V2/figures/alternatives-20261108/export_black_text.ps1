param(
    [string]$Source = (Join-Path $PSScriptRoot 'MPSLGP_Editable_Figures.pptx'),
    [string]$Output = (Join-Path $PSScriptRoot 'MPSLGP_Editable_Figures_black')
)

$ErrorActionPreference = 'Stop'
$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$before = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
$script:textShapes = 0
$script:outlineShapes = 0

function Set-BlackTextAndOutline {
    param($Shape)

    if ($Shape.Type -eq 6) {
        for ($index = 1; $index -le $Shape.GroupItems.Count; $index++) {
            Set-BlackTextAndOutline -Shape $Shape.GroupItems.Item([int]$index)
        }
    }
    if ($Shape.HasTextFrame -eq -1 -and $Shape.TextFrame.HasText -eq -1) {
        $Shape.TextFrame.TextRange.Font.Color.RGB = 0
        $Shape.TextFrame2.TextRange.Font.Fill.ForeColor.RGB = 0
        $script:textShapes++
    }
    if ($Shape.Line.Visible -eq -1) {
        $Shape.Line.ForeColor.RGB = 0
        $script:outlineShapes++
    }
}

$powerpoint = New-Object -ComObject PowerPoint.Application
$presentation = $null
try {
    $presentation = $powerpoint.Presentations.Open($sourcePath, -1, 0, 0)
    if ($presentation.Slides.Count -ne 4) { throw 'Expected the current four-slide figure deck.' }
    for ($slideIndex = 1; $slideIndex -le $presentation.Slides.Count; $slideIndex++) {
        $slide = $presentation.Slides.Item([int]$slideIndex)
        for ($shapeIndex = 1; $shapeIndex -le $slide.Shapes.Count; $shapeIndex++) {
            Set-BlackTextAndOutline -Shape $slide.Shapes.Item([int]$shapeIndex)
        }
    }
    $presentation.SaveAs($Output + '.pptx', 24)
    $presentation.SaveAs($Output + '.pdf', 32)
    if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash -ne $before) {
        throw 'Original editable figure deck changed unexpectedly.'
    }
    Write-Output "PASS: four slides exported; $script:textShapes text shapes and $script:outlineShapes outlines black; original source unchanged."
}
finally {
    if ($null -ne $presentation) {
        $presentation.Close()
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($presentation)
    }
    if ($powerpoint.Presentations.Count -eq 0) { $powerpoint.Quit() }
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($powerpoint)
}