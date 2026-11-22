$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$reference = Join-Path $root 'reference.pptx'
$referenceHash = (Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash
$output = Join-Path $root 'MPSLGP_Preference_Region_Transfer'
$combinedPath = Join-Path $root 'MPSLGP_Editable_Figures.pptx'
$combinedPdf = Join-Path $root 'MPSLGP_Editable_Figures.pdf'
$combinedHash = (Get-FileHash -LiteralPath $combinedPath -Algorithm SHA256).Hash
$mergePath = Join-Path $root 'MPSLGP_Editable_Figures.merge.pptx'
$mergePdf = Join-Path $root 'MPSLGP_Editable_Figures.merge.pdf'
if ((Test-Path -LiteralPath $mergePath) -or (Test-Path -LiteralPath $mergePdf)) {
    throw 'A pending merge file exists. Inspect it before rebuilding the combined deck.'
}
$font = 'Comic Sans MS'
$designWidth = 1296
$designHeight = 920
$slideWidth = 1944
$slideHeight = 1188
$titleFontSize = [single]28
$contentFontSize = [single]24
$ink = '111111'
$blue = '2563EB'
$orange = 'F97316'
$purple = '6E3FA8'
$gray = '64748B'
$lambda = [string][char]0x03BB
$arrow = [string][char]0x2192
$leftArrow = [string][char]0x2190
$sub1 = [string][char]0x2081
$sub2 = [string][char]0x2082
$sub0 = [string][char]0x2080
$intersection = [string][char]0x2229
$textShapes = [Collections.Generic.List[object]]::new()
$titleShapeNames = [Collections.Generic.HashSet[string]]::new()

function Color([string]$hex) {
    return [Convert]::ToInt32($hex.Substring(0, 2), 16) +
        256 * [Convert]::ToInt32($hex.Substring(2, 2), 16) +
        65536 * [Convert]::ToInt32($hex.Substring(4, 2), 16)
}

function Label($name, $x, $y, $w, $h, $text, $color = $ink, $bold = $false, $align = 2, [switch]$Title) {
    $shape = $slide.Shapes.AddTextbox(1, $x, $y, $w, $h)
    $shape.Name = $name
    $frame = $shape.TextFrame
    $frame.MarginLeft = 0
    $frame.MarginRight = 0
    $frame.MarginTop = 0
    $frame.MarginBottom = 0
    $frame.WordWrap = -1
    $frame.AutoSize = 0
    $frame.VerticalAnchor = 3
    $frame.TextRange.Text = $text
    $frame.TextRange.Font.Name = $font
    $frame.TextRange.Font.Size = if ($Title) { $titleFontSize } else { $contentFontSize }
    $frame.TextRange.Font.Color.RGB = Color $color
    $frame.TextRange.Font.Bold = if ($bold) { -1 } else { 0 }
    $frame.TextRange.ParagraphFormat.Alignment = $align
    $frame.TextRange.ParagraphFormat.SpaceBefore = 0
    $frame.TextRange.ParagraphFormat.SpaceAfter = 0
    $shape.Left = [single]$x
    $shape.Top = [single]$y
    $shape.Width = [single]$w
    $shape.Height = [single]$h
    $textShapes.Add($shape)
    if ($Title) { [void]$titleShapeNames.Add($name) }
}

function Shape($name, $x, $y, $w, $h, $fill = 'FFFFFF', $stroke = $gray, $kind = 5, $weight = 1.5, $transparency = 0) {
    $shape = $slide.Shapes.AddShape($kind, $x, $y, $w, $h)
    $shape.Name = $name
    if ($kind -eq 5) { $shape.Adjustments.Item(1) = [single]0.10 }
    if ($fill) {
        $shape.Fill.Solid()
        $shape.Fill.ForeColor.RGB = Color $fill
        $shape.Fill.Transparency = [single]$transparency
    } else { $shape.Fill.Visible = 0 }
    if ($stroke) {
        $shape.Line.ForeColor.RGB = Color $stroke
        $shape.Line.Weight = [single]$weight
    } else { $shape.Line.Visible = 0 }
}

function Line($name, $x1, $y1, $x2, $y2, $color = $ink, $weight = 2, $head = $true, $dashed = $false) {
    $shape = $slide.Shapes.AddLine($x1, $y1, $x2, $y2)
    $shape.Name = $name
    $shape.Line.ForeColor.RGB = Color $color
    $shape.Line.Weight = [single]$weight
    if ($head) { $shape.Line.EndArrowheadStyle = 2 }
    if ($dashed) { $shape.Line.DashStyle = 4 }
}

function Curve($name, $points, $color, $weight = 3, $dashed = $false, $halo = $false) {
    $builder = $slide.Shapes.BuildFreeform(1, $points[0][0], $points[0][1])
    $builder.AddNodes(1, 1, $points[1][0], $points[1][1],
        $points[2][0], $points[2][1], $points[3][0], $points[3][1])
    $shape = $builder.ConvertToShape()
    $shape.Name = $name
    $shape.Fill.Visible = 0
    $shape.Line.ForeColor.RGB = Color $color
    $shape.Line.Weight = [single]$weight
    if ($halo) {
        $shape.Line.Transparency = [single]0.85
    } else {
        $shape.Line.EndArrowheadStyle = 2
        if ($dashed) { $shape.Line.DashStyle = 4 }
    }
}

function EdgeSide($a, $b, $point) {
    return ($b[0] - $a[0]) * ($point[1] - $a[1]) -
        ($b[1] - $a[1]) * ($point[0] - $a[0])
}

function IntersectRegions($subject, $clip) {
    # Clip the convex footprints so purple is their actual geometric intersection.
    $vertices = $subject
    for ($i = 0; $i -lt $clip.Count; $i++) {
        $a = $clip[$i]
        $b = $clip[($i + 1) % $clip.Count]
        $result = [Collections.Generic.List[object]]::new()
        if ($vertices.Count -eq 0) { break }
        $previous = $vertices[-1]
        $previousSide = EdgeSide $a $b $previous
        foreach ($current in $vertices) {
            $currentSide = EdgeSide $a $b $current
            if (($currentSide -ge 0) -ne ($previousSide -ge 0)) {
                $fraction = $previousSide / ($previousSide - $currentSide)
                $result.Add(@(
                    ($previous[0] + $fraction * ($current[0] - $previous[0])),
                    ($previous[1] + $fraction * ($current[1] - $previous[1]))
                ))
            }
            if ($currentSide -ge 0) { $result.Add($current) }
            $previous = $current
            $previousSide = $currentSide
        }
        $vertices = $result.ToArray()
    }
    return ,$vertices
}

function Region($name, $x, $vertices, $fill, $stroke, $transparency = 0.30) {
    $builder = $slide.Shapes.BuildFreeform(1, ($x + $vertices[0][0]), $vertices[0][1])
    for ($i = 1; $i -le $vertices.Count; $i++) {
        $point = $vertices[$i % $vertices.Count]
        $builder.AddNodes(0, 0, ($x + $point[0]), $point[1])
    }
    $shape = $builder.ConvertToShape()
    $shape.Name = $name
    if ($fill) {
        $shape.Fill.Solid()
        $shape.Fill.ForeColor.RGB = Color $fill
        $shape.Fill.Transparency = [single]$transparency
    } else { $shape.Fill.Visible = 0 }
    $shape.Line.ForeColor.RGB = Color $stroke
    $shape.Line.Weight = [single]1.5
}

function Tree($name, $x, $y, $accent, $transfer = $false, $scale = 1) {
    Line "$name left edge" ($x + 24 * $scale) $y $x ($y + 30 * $scale) $accent 1.8 $false
    Line "$name right edge" ($x + 24 * $scale) $y ($x + 48 * $scale) ($y + 30 * $scale) $accent 1.8 $false
    Shape "$name root" ($x + 14 * $scale) ($y - 10 * $scale) (20 * $scale) (20 * $scale) $accent '' 9
    Shape "$name left terminal" ($x - 10 * $scale) ($y + 20 * $scale) (20 * $scale) (20 * $scale) $accent '' 9
    $leaf = if ($transfer) { $orange } else { $accent }
    Shape "$name right terminal" ($x + 38 * $scale) ($y + 20 * $scale) (20 * $scale) (20 * $scale) $leaf '' 9
}

function RuleNode($name, $x, $y, $accent, $transfer = $false) {
    $pale = if ($accent -eq $orange) { 'FFF1E3' } else { 'EDF3FF' }
    Shape "$name halo" ($x - 49) ($y - 49) 98 98 $pale '' 9
    Shape "$name ring" ($x - 39) ($y - 39) 78 78 'FFFFFF' $accent 9 2.3
    Tree "$name sequencing" ($x - 28) ($y - 8) $accent $transfer 0.45
    Tree "$name routing" ($x + 6) ($y - 8) $accent $false 0.45
}

function SearchMap($name, $x, $region, $useful) {
    $heading = if ($useful) { '(b) More cross-task mating' } else { '(c) More same-task mating' }
    Label "$name heading" $x 170 600 36 $heading $ink $true -Title
    Shape "$name common representation" $x 220 600 354 'FAFBFD' 'CFD7E3' 5
    Shape "$name context chip" ($x + 16) 232 142 40 'E8DCF3' '' 5
    Label "$name region" ($x + 16) 234 142 35 "k = $region" $purple $true
    $utility = if ($useful) { "U(1,2,$region) > U$sub0" } else { "U(1,2,$region) < U$sub0" }
    Label "$name utility" ($x + 192) 234 382 35 $utility $(if ($useful) { 'C2410C' } else { $blue })

    $task1 = @(@(42, 354), @(108, 320), @(230, 312), @(329, 345),
        @(392, 416), @(408, 472), @(350, 517), @(220, 527),
        @(85, 526), @(34, 487), @(24, 414))
    $task2 = @(@(268, 349), @(365, 313), @(476, 311), @(564, 351),
        @(582, 426), @(562, 498), @(490, 526), @(363, 526),
        @(269, 508), @(229, 433), @(235, 381))
    $overlap = IntersectRegions $task1 $task2
    if ($overlap.Count -lt 3) { throw 'Task footprints must have a nonempty overlap.' }
    foreach ($footprint in @($task1, $task2)) {
        foreach ($point in $overlap) {
            for ($i = 0; $i -lt $footprint.Count; $i++) {
                if ((EdgeSide $footprint[$i] $footprint[($i + 1) % $footprint.Count] $point) -lt -0.001) {
                    throw 'Overlap extends outside a task search region.'
                }
            }
        }
    }
    Region "$name Task 1 search region S1" $x $task1 'D8E6FF' $blue
    Region "$name Task 2 search region S2" $x $task2 'F9E3C9' $orange
    Region "$name shared search region S1 intersection S2" $x $overlap 'CDB8E5' $purple 0.10
    Region "$name Task 1 boundary" $x $task1 '' $blue
    Region "$name Task 2 boundary" $x $task2 '' $orange

    $nodes = @(@(35, 403), @(205, 394), @(277, 327), @(370, 355), @(554, 427), @(453, 513), @(205, 530))
    $edges = @(@(0, 1), @(1, 2), @(1, 6), @(2, 3), @(3, 4), @(3, 5), @(4, 5), @(5, 6))
    foreach ($edge in $edges) {
        $a = $nodes[$edge[0]]
        $b = $nodes[$edge[1]]
        Line "$name symbolic neighbour edge $($edge[0]) $($edge[1])" ($x + $a[0]) $a[1] ($x + $b[0]) $b[1] 'CCD4E3' 1.4 $false
    }
    foreach ($node in $nodes) {
        Shape "$name unselected neighbour $($node[0])" ($x + $node[0] - 5) ($node[1] - 5) 10 10 'CCD4E3' '' 9
    }

    $primaryPath = @(@(($x + 150), 378), @(($x + 211), 386), @(($x + 265), 423), @(($x + 307), 450))
    $donorPath = @(@(($x + 479), 394), @(($x + 470), 455), @(($x + 431), 478), @(($x + 382), 478))
    $localPath = @(@(($x + 133), 495), @(($x + 189), 526), @(($x + 253), 526), @(($x + 302), 492))
    Curve "$name primary contribution" $primaryPath $blue 3
    if ($useful) {
        Curve "$name transfer glow" $donorPath $orange 14 $false $true
        Curve "$name emphasized donor contribution" $donorPath $orange 5
        Curve "$name local alternative" $localPath '93B4EF' 1.7 $true
    } else {
        Curve "$name donor alternative" $donorPath 'EAAF66' 1.7 $true
        Curve "$name local glow" $localPath $blue 14 $false $true
        Curve "$name emphasized local contribution" $localPath $blue 5
    }
    RuleNode "$name primary parent" ($x + 115) 354 $blue
    RuleNode "$name donor parent" ($x + 488) 354 $orange
    RuleNode "$name local parent" ($x + 91) 482 $blue
    RuleNode "$name example child" ($x + 340) 478 $blue $useful
    Label "$name primary task" ($x + 8) 283 206 34 "Task 1: S$sub1" $blue $true
    Label "$name overlap label" ($x + 215) 283 180 34 "S$sub1 $intersection S$sub2" $purple $true
    Line "$name overlap pointer" ($x + 305) 318 ($x + 305) 366 $purple 1.5
    Label "$name donor task" ($x + 398) 283 194 34 "Task 2: S$sub2" 'C2410C' $true
    Label "$name local parent label" ($x + 8) 537 194 34 'Local parent' $blue
    Label "$name example child label" ($x + 224) 537 256 34 'Child: task 1' $ink
}

$app = New-Object -ComObject PowerPoint.Application
$deck = $null
$combined = $null
try {
    $deck = $app.Presentations.Add(0)
    $deck.PageSetup.SlideWidth = $slideWidth
    $deck.PageSetup.SlideHeight = $slideHeight
    $slide = $deck.Slides.Add(1, 12)
    $slide.Name = 'MPSLGP_Regional_Transfer_Figure'
    $slide.FollowMasterBackground = 0
    $slide.Background.Fill.Solid()
    $slide.Background.Fill.ForeColor.RGB = Color 'FFFFFF'

    Label 'Preference heading' 32 10 1232 38 '(a) Preference-indexed transfer' $ink $true 1 -Title
    $cellWidth = 104
    for ($k = 0; $k -lt 5; $k++) {
        $active = $k -eq 3
        Shape "Preference region $k" (32 + $k * $cellWidth) 68 $cellWidth 44 $(if ($active) { 'E8DCF3' } else { 'F3F4F6' }) $(if ($active) { $purple } else { 'CFD7E3' }) 1 $(if ($active) { 2.5 } else { 1 })
        Label "Preference region $k label" (32 + $k * $cellWidth) 71 $cellWidth 36 "k = $k" $(if ($active) { $purple } else { $gray }) $active
    }
    Label 'Preference direction' 32 118 520 38 "Objective 2 $leftArrow $lambda$sub1 $arrow Objective 1" $gray
    Label 'Preference example' 578 62 686 38 "Next evaluation: $lambda$sub1 = 0.70 $arrow k = 3" $purple $true
    Label 'Gate index' 578 100 686 38 "Compare U(r$sub0,d,k) with U$sub0  (K = 5)" $ink
    Line 'Current region guide' 396 158 396 168 $purple 1.6

    SearchMap 'Region 3' 32 3 $true
    SearchMap 'Region 1' 664 1 $false
    Label 'Common rule space' 32 588 1232 38 "S$sub1, S$sub2 lie in shared GP space X; overlap alone does not decide transfer." $gray

    Label 'Feedback heading' 32 636 1232 38 '(d) Actual offspring update the transfer gate' $ink $true 1 -Title
    $positions = @(32, 347, 662, 977)
    $titles = @('One-tree crossover', 'Task inheritance', 'Local evaluation', 'Utility feedback')
    for ($i = 0; $i -lt 4; $i++) {
        Shape "Step $i panel" $positions[$i] 688 285 174 'FFFFFF' $(if ($i -eq 3) { $purple } else { 'B4C7E7' }) 5
        Shape "Step $i heading background" ($positions[$i] + 2) 690 281 42 $(if ($i -eq 3) { 'E8DCF3' } else { 'EDF3FF' }) '' 5
        Label "Step $i title" ($positions[$i] + 2) 691 281 38 $titles[$i] $ink $true -Title
        if ($i -lt 3) { Line "Step $i to next" ($positions[$i] + 287) 789 ($positions[$i + 1] - 4) 789 $gray 2.1 }
    }
    Tree 'Sequencing with donor fragment' 93 761 $blue $true
    Tree 'Unchanged routing tree' 213 761 $blue
    Label 'Changed tree' 47 808 133 38 'change' $gray
    Label 'Unchanged tree' 178 808 128 38 'keep' $gray
    Label 'Inheritance content' 357 742 265 111 "Node shares set r`nRecord event ID`nCredit follows r" $ink
    Label 'Evaluation content' 671 742 267 111 "Preselect + simulate`nSame task + seed`nReal local baseline" $ink
    Label 'Update content' 986 742 267 111 "Survival + gain`nFailure penalties`nGroup by (r,d,k)" $ink

    Line 'Feedback down' 1117 864 1117 892 $purple 2 $false $true
    Line 'Feedback return' 1117 892 16 892 $purple 2 $false $true
    Line 'Feedback up' 16 892 16 53 $purple 2 $false $true
    Line 'Feedback into preference gate' 16 53 578 53 $purple 2 $true $true
    Shape 'Feedback caption white backing' 148 873 938 38 'FFFFFF' '' 1
    Label 'Feedback caption' 148 873 938 38 'Count each event once; update utilities for the next gate.' $purple

    $slide.NotesPage.Shapes.Placeholders.Item(2).TextFrame.TextRange.Text = @'
Preference-region-aware transfer in MPSLGP. All diagram elements are native editable PowerPoint shapes and text.
All visible figure text uses Comic Sans MS: the four panel headings and four workflow card titles are 28 pt, and all other labels and content are 24 pt, matching the original three slides. The canvas is 1944 x 1188 pt, also matching that deck. Scaling to IEEEtran's 516 TeX pt text width gives approximately 7.43 pt titles and 6.37 pt content.
The three-node tree circles are enlarged from 12 to 20 design units in diameter. Uniform geometry scaling preserves circular nodes and the shape of the search-space overlap.
Blue and orange identify task-labelled GP groups sharing one symbolic representation, not disjoint feasible domains. Each highlighted node contains sequencing and routing trees.
The irregular blue and orange footprints S1 and S2 depict task-specific explored regions within the common GP rule space X. Their purple overlap is the geometrically clipped intersection, not a separately positioned decoration.
The same footprints and intersection appear in both preference panels deliberately: structural search-space overlap alone does not imply beneficial transfer. Utilities learned from offspring determine the preference-dependent mating probability.
The blue-only and orange-only areas represent exploration outside the shared region. These conceptual footprints are not extra constraints on which expressions either task can represent.
The gray network depicts conceptual structural neighbours, not a computed embedding, distance metric or transfer criterion.
Panels (b) and (c) are illustrative utility states at different preferences/generations. Thick curves highlight a more likely parental contribution route; thin dashed curves remain available alternatives.
The budgeted softmax gate uses learned directed utility and a no-transfer reference. No probability is forced to zero or one by the illustration.
The example child retains primary task r0=1; it can instead inherit the secondary task when its node contribution reaches the threshold. Feedback uses final task r and the other contributing task d.
Each valid cross-task offspring is recorded once; discarded candidates contribute to the denominator and penalty. Survival is assessed after preselection, real evaluation and enabled clearing.
The scalar improvement baseline uses the same task, generation, preference and simulation seed as the child. Utility is updated by (r,d,k) group, not per individual.
One common tree index undergoes subtree crossover; the other complete tree is swapped between the parents. No auxiliary transfer-assessment simulations are required.
'@

    $scale = [math]::Min($slideWidth / $designWidth, $slideHeight / $designHeight)
    $offsetX = ($slideWidth - $designWidth * $scale) / 2
    $offsetY = ($slideHeight - $designHeight * $scale) / 2
    $drawing = $slide.Shapes.Range().Group()
    $left = $drawing.Left
    $top = $drawing.Top
    $drawing.LockAspectRatio = -1
    $drawing.Width = [single]($drawing.Width * $scale)
    $drawing.Left = [single]($offsetX + $left * $scale)
    $drawing.Top = [single]($offsetY + $top * $scale)
    [void]$drawing.Ungroup()
    $textShapes.Clear()
    foreach ($shape in $slide.Shapes) {
        if ($shape.HasTextFrame -and $shape.TextFrame.HasText) {
            $shape.TextFrame.AutoSize = 0
            $shape.TextFrame.TextRange.Font.Size = if ($titleShapeNames.Contains($shape.Name)) {
                $titleFontSize
            } else { $contentFontSize }
            $textShapes.Add($shape)
        }
    }

    $slide.Export("$output.png", 'PNG', ($slideWidth * 2), ($slideHeight * 2))
    $overflow = @($textShapes | Where-Object {
        $_.TextFrame.TextRange.BoundHeight -gt $_.Height + 1 -or
        $_.TextFrame.TextRange.BoundWidth -gt $_.Width + 2
    } | ForEach-Object {
        '{0}: text {1:N1}x{2:N1}, box {3:N1}x{4:N1}' -f $_.Name,
            $_.TextFrame.TextRange.BoundWidth, $_.TextFrame.TextRange.BoundHeight, $_.Width, $_.Height
    })
    if ($overflow.Count) { throw ('Text exceeds its box: ' + ($overflow -join ', ')) }
    foreach ($shape in $textShapes) {
        $expectedSize = if ($titleShapeNames.Contains($shape.Name)) { $titleFontSize } else { $contentFontSize }
        if ([math]::Abs($shape.TextFrame.TextRange.Font.Size - $expectedSize) -gt 0.01) {
            throw ('Inconsistent font size: ' + $shape.Name)
        }
    }
    foreach ($shape in $slide.Shapes) {
        if ($shape.Left -lt 0 -or $shape.Top -lt 0 -or
            $shape.Left + $shape.Width -gt $slideWidth + 0.5 -or $shape.Top + $shape.Height -gt $slideHeight + 0.5) {
            throw ('Shape outside slide: ' + $shape.Name)
        }
    }
    $deck.SaveAs("$output.pptx", 24)
    $deck.SaveAs("$output.pdf", 32)

    Copy-Item -LiteralPath $combinedPath -Destination $mergePath
    $combined = $app.Presentations.Open($mergePath, 0, 0, 0)
    if ($combined.PageSetup.SlideWidth -ne $slideWidth -or
        $combined.PageSetup.SlideHeight -ne $slideHeight) {
        throw 'The combined deck canvas no longer matches Figure 1.'
    }
    $replaceFigure = $combined.Slides.Count -eq 4 -and
        $combined.Slides.Item(4).Name -eq 'MPSLGP_Regional_Transfer_Figure'
    if ($combined.Slides.Count -ne 3 -and -not $replaceFigure) {
        throw 'Expected three original slides, optionally followed by the generated Figure 1.'
    }
    if ($combined.Slides.InsertFromFile("$output.pptx", 3, 1, 1) -ne 1) {
        throw 'Figure 1 could not be inserted into the combined deck.'
    }
    if ($replaceFigure) { $combined.Slides.Item(5).Delete() }
    $combined.Slides.Item(4).Name = 'MPSLGP_Regional_Transfer_Figure'
    $combined.Save()
    $combined.SaveAs($mergePdf, 32)
    $combined.Close()
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($combined)
    $combined = $null
    if ((Get-FileHash -LiteralPath $combinedPath -Algorithm SHA256).Hash -ne $combinedHash) {
        throw 'The original combined deck changed during generation; the staged merge was not applied.'
    }
    Move-Item -LiteralPath $mergePath -Destination $combinedPath -Force
    Move-Item -LiteralPath $mergePdf -Destination $combinedPdf -Force
    "Saved editable figure: $output.pptx"
    "Saved combined deck with Figure 1 as slide 4: $combinedPath"
    "Native shapes: $($slide.Shapes.Count); text boxes: $($textShapes.Count)"
    "Title font: $titleFontSize pt ($($titleShapeNames.Count) headings); content font: $contentFontSize pt"
} finally {
    if ($null -ne $combined) {
        $combined.Saved = -1
        $combined.Close()
        [void][Runtime.InteropServices.Marshal]::ReleaseComObject($combined)
    }
    if ($null -ne $deck) { $deck.Close(); [void][Runtime.InteropServices.Marshal]::ReleaseComObject($deck) }
    [void][Runtime.InteropServices.Marshal]::ReleaseComObject($app)
}
if ((Get-FileHash -LiteralPath $reference -Algorithm SHA256).Hash -ne $referenceHash) {
    throw 'Reference deck changed unexpectedly.'
}
