Add-Type -AssemblyName System.Drawing

$outDir = Join-Path (Get-Location) "play_store_assets"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function New-Brush($hex) {
    return New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function New-Pen($hex, $width = 1) {
    $pen = New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml($hex)), $width
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    return $pen
}

function Add-RoundedRectPath($x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Fill-RoundedRect($g, $x, $y, $w, $h, $r, $brush, $pen = $null) {
    $path = Add-RoundedRectPath $x $y $w $h $r
    $g.FillPath($brush, $path)
    if ($pen -ne $null) { $g.DrawPath($pen, $path) }
    $path.Dispose()
}

function Draw-Text($g, $text, $font, $brush, $x, $y, $w = 900, $h = 80) {
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Near
    $format.LineAlignment = [System.Drawing.StringAlignment]::Near
    $g.DrawString($text, $font, $brush, (New-Object System.Drawing.RectangleF($x, $y, $w, $h)), $format)
    $format.Dispose()
}

function Draw-Mark($g, $x, $y, $scale) {
    $white = New-Pen "#FFFFFF" (13 * $scale)
    $white2 = New-Pen "#FFFFFF" (8 * $scale)
    $white2.Color = [System.Drawing.Color]::FromArgb(145, 255, 255, 255)
    $p1 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p1.AddBezier($x + 32*$scale, $y + 88*$scale, $x + 50*$scale, $y + 62*$scale, $x + 68*$scale, $y + 64*$scale, $x + 85*$scale, $y + 80*$scale)
    $p1.AddBezier($x + 85*$scale, $y + 80*$scale, $x + 102*$scale, $y + 96*$scale, $x + 115*$scale, $y + 42*$scale, $x + 138*$scale, $y + 52*$scale)
    $p1.AddBezier($x + 138*$scale, $y + 52*$scale, $x + 152*$scale, $y + 58*$scale, $x + 158*$scale, $y + 71*$scale, $x + 168*$scale, $y + 62*$scale)
    $g.DrawPath($white, $p1)
    $p2 = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p2.AddBezier($x + 32*$scale, $y + 113*$scale, $x + 56*$scale, $y + 94*$scale, $x + 77*$scale, $y + 100*$scale, $x + 96*$scale, $y + 109*$scale)
    $p2.AddBezier($x + 96*$scale, $y + 109*$scale, $x + 118*$scale, $y + 119*$scale, $x + 133*$scale, $y + 84*$scale, $x + 160*$scale, $y + 88*$scale)
    $g.DrawPath($white2, $p2)
    $dot = New-Brush "#FFFFFF"
    $g.FillEllipse($dot, $x + 97*$scale, $y + 38*$scale, 16*$scale, 16*$scale)
    $dot.Dispose(); $white.Dispose(); $white2.Dispose(); $p1.Dispose(); $p2.Dispose()
}

$bitmap = New-Object System.Drawing.Bitmap 1024, 500
$g = [System.Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([System.Drawing.ColorTranslator]::FromHtml("#F8FCF8"))

$gridPen = New-Pen "#DCE9E2" 1
for ($x = 0; $x -le 1024; $x += 32) { $g.DrawLine($gridPen, $x, 0, $x, 500) }
for ($y = 0; $y -le 500; $y += 32) { $g.DrawLine($gridPen, 0, $y, 1024, $y) }
$gridPen.Dispose()

$deep = New-Brush "#087A5A"
$green = New-Brush "#129767"
$dark = New-Brush "#10241D"
$muted = New-Brush "#5E716A"
$accent = New-Brush "#007A55"
$white = New-Brush "#FFFFFF"
$line = New-Pen "#D8E3DD" 2

Fill-RoundedRect $g 72 92 184 184 40 $deep
Fill-RoundedRect $g 86 106 156 156 30 $green
Draw-Mark $g 72 92 0.96

$titleFont = New-Object System.Drawing.Font "Arial", 58, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$subFont = New-Object System.Drawing.Font "Arial", 26, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
$headlineFont = New-Object System.Drawing.Font "Arial", 30, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$cardFont = New-Object System.Drawing.Font "Arial", 24, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$smallFont = New-Object System.Drawing.Font "Arial", 18, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)

Draw-Text $g "Fynlo Ledger" $titleFont $dark 300 86 690 74
Draw-Text $g "Ledger-first personal finance" $subFont $muted 304 166 620 42
Draw-Text $g "Track every rupee with clear records," $headlineFont $accent 304 244 680 42
Draw-Text $g "audit trails and useful reports." $headlineFont $accent 304 284 680 42

$cardY = 358
$cardSpecs = @(
    @{ X = 304; Label = "Cash"; Icon = "+"; Fill = "#E4F5EC"; Color = "#007A55" },
    @{ X = 520; Label = "Loans"; Icon = "<>"; Fill = "#F7E8B8"; Color = "#9A6A08" },
    @{ X = 736; Label = "Reports"; Icon = "OK"; Fill = "#E4F5EC"; Color = "#007A55" }
)

foreach ($spec in $cardSpecs) {
    Fill-RoundedRect $g $spec.X $cardY 188 72 22 $white $line
    $iconBrush = New-Brush $spec.Fill
    Fill-RoundedRect $g ($spec.X + 18) ($cardY + 17) 38 38 11 $iconBrush
    $iconBrush.Dispose()
    $iconColor = New-Brush $spec.Color
    Draw-Text $g $spec.Icon $smallFont $iconColor ($spec.X + 28) ($cardY + 26) 34 24
    Draw-Text $g $spec.Label $cardFont $dark ($spec.X + 72) ($cardY + 23) 110 34
    $iconColor.Dispose()
}

Draw-Text $g "Built for expenses, loans, debts, investments, net worth and exports." $smallFont $muted 304 454 680 28

$png = Join-Path $outDir "feature_graphic_1024x500.png"
$bitmap.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)

$g.Dispose()
$bitmap.Dispose()
$deep.Dispose(); $green.Dispose(); $dark.Dispose(); $muted.Dispose(); $accent.Dispose(); $white.Dispose(); $line.Dispose()
$titleFont.Dispose(); $subFont.Dispose(); $headlineFont.Dispose(); $cardFont.Dispose(); $smallFont.Dispose()

Write-Host "Wrote $png"
