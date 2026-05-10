package app.fynlo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fynlo.ui.theme.*

enum class EmptyStateType { LENDING, DEBTS, INVESTMENTS, SPENDING }

@Composable
fun EmptyStateIllustration(
    type: EmptyStateType,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "Get Started"
) {
    val primary  = MaterialTheme.colorScheme.primary
    val surface  = MaterialTheme.colorScheme.surfaceVariant
    val outline  = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Illustration canvas
        Canvas(modifier = Modifier.size(180.dp)) {
            when (type) {
                EmptyStateType.LENDING     -> drawLendingIllustration(primary, surface, outline)
                EmptyStateType.DEBTS       -> drawDebtsIllustration(primary, surface, outline)
                EmptyStateType.INVESTMENTS -> drawInvestmentsIllustration(primary, surface, outline)
                EmptyStateType.SPENDING    -> drawSpendingIllustration(primary, surface, outline)
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = when (type) {
                EmptyStateType.LENDING     -> "No active loans"
                EmptyStateType.DEBTS       -> "Debt-free!"
                EmptyStateType.INVESTMENTS -> "No investments yet"
                EmptyStateType.SPENDING    -> "No expenses this month"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = when (type) {
                EmptyStateType.LENDING     -> "Track money you've lent and earn interest. Add a borrower to get started."
                EmptyStateType.DEBTS       -> "You have no outstanding debts. Add one to track loans you've taken from others."
                EmptyStateType.INVESTMENTS -> "Grow your wealth. Track FDs, stocks, real estate, gold and more."
                EmptyStateType.SPENDING    -> "Log your expenses to get category breakdowns and spending insights."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (onAction != null) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

// ── Lending: person handing a coin to another person ─────────────────────────

private fun DrawScope.drawLendingIllustration(primary: Color, surface: Color, outline: Color) {
    val w = size.width; val h = size.height
    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thinStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Background circle
    drawCircle(surface, radius = w * 0.42f, center = Offset(w / 2, h / 2))

    // Left person (lender)
    val lx = w * 0.28f
    // Head
    drawCircle(primary.copy(alpha = 0.7f), radius = w * 0.08f, center = Offset(lx, h * 0.28f))
    // Body
    val lBody = Path().apply {
        moveTo(lx, h * 0.38f)
        lineTo(lx, h * 0.60f)
    }
    drawPath(lBody, primary.copy(alpha = 0.7f), style = stroke)
    // Arm extended right (handing coin)
    val lArm = Path().apply {
        moveTo(lx, h * 0.45f)
        lineTo(lx + w * 0.15f, h * 0.50f)
    }
    drawPath(lArm, primary.copy(alpha = 0.7f), style = stroke)
    // Legs
    drawLine(primary.copy(alpha = 0.7f), Offset(lx, h * 0.60f), Offset(lx - w * 0.07f, h * 0.76f), 3.dp.toPx())
    drawLine(primary.copy(alpha = 0.7f), Offset(lx, h * 0.60f), Offset(lx + w * 0.05f, h * 0.76f), 3.dp.toPx())

    // Coin in the middle
    drawCircle(SemanticAmber, radius = w * 0.06f, center = Offset(w / 2, h * 0.50f))
    drawCircle(SemanticAmber.copy(alpha = 0.4f), radius = w * 0.06f, center = Offset(w / 2, h * 0.50f), style = Stroke(2.dp.toPx()))
    // ₹ suggestion — small inner ring
    drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.025f, center = Offset(w / 2, h * 0.50f))

    // Right person (borrower)
    val rx = w * 0.72f
    drawCircle(SemanticBlue.copy(alpha = 0.7f), radius = w * 0.08f, center = Offset(rx, h * 0.28f))
    val rBody = Path().apply {
        moveTo(rx, h * 0.38f); lineTo(rx, h * 0.60f)
    }
    drawPath(rBody, SemanticBlue.copy(alpha = 0.7f), style = stroke)
    // Arm extended left (receiving)
    val rArm = Path().apply {
        moveTo(rx, h * 0.45f); lineTo(rx - w * 0.15f, h * 0.50f)
    }
    drawPath(rArm, SemanticBlue.copy(alpha = 0.7f), style = stroke)
    drawLine(SemanticBlue.copy(alpha = 0.7f), Offset(rx, h * 0.60f), Offset(rx + w * 0.07f, h * 0.76f), 3.dp.toPx())
    drawLine(SemanticBlue.copy(alpha = 0.7f), Offset(rx, h * 0.60f), Offset(rx - w * 0.05f, h * 0.76f), 3.dp.toPx())

    // Arrow from lender to borrower
    drawLine(primary.copy(alpha = 0.3f), Offset(w * 0.38f, h * 0.50f), Offset(w * 0.58f, h * 0.50f), 1.5.dp.toPx())
}

// ── Debts: a document/bill with a green check ────────────────────────────────

private fun DrawScope.drawDebtsIllustration(primary: Color, surface: Color, outline: Color) {
    val w = size.width; val h = size.height
    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Background circle
    drawCircle(surface, radius = w * 0.42f, center = Offset(w / 2, h / 2))

    // Document body
    val docL = w * 0.28f; val docT = h * 0.20f
    val docW = w * 0.44f; val docH = h * 0.55f
    drawRoundRect(primary.copy(alpha = 0.12f), Offset(docL, docT), Size(docW, docH), CornerRadius(8.dp.toPx()))
    drawRoundRect(primary.copy(alpha = 0.4f), Offset(docL, docT), Size(docW, docH), CornerRadius(8.dp.toPx()), style = stroke)

    // Folded corner
    val foldSize = w * 0.10f
    val foldPath = Path().apply {
        moveTo(docL + docW - foldSize, docT)
        lineTo(docL + docW, docT + foldSize)
        lineTo(docL + docW - foldSize, docT + foldSize)
        close()
    }
    drawPath(foldPath, primary.copy(alpha = 0.2f))
    drawPath(foldPath, primary.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx()))

    // Lines on document (text rows)
    val lineColor = primary.copy(alpha = 0.2f)
    for (i in 0..3) {
        val lineY = docT + h * 0.14f + i * h * 0.08f
        val lineW = if (i == 3) docW * 0.45f else docW * 0.75f
        drawRoundRect(lineColor, Offset(docL + docW * 0.12f, lineY), Size(lineW, 5.dp.toPx()), CornerRadius(3.dp.toPx()))
    }

    // Big green check circle
    drawCircle(Emerald500.copy(alpha = 0.15f), radius = w * 0.16f, center = Offset(w * 0.68f, h * 0.72f))
    drawCircle(Emerald500, radius = w * 0.16f, center = Offset(w * 0.68f, h * 0.72f), style = Stroke(2.5.dp.toPx()))
    // Checkmark
    val checkPath = Path().apply {
        moveTo(w * 0.59f, h * 0.72f)
        lineTo(w * 0.66f, h * 0.79f)
        lineTo(w * 0.78f, h * 0.64f)
    }
    drawPath(checkPath, Emerald500, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ── Investments: upward bar chart with rising trend line ─────────────────────

private fun DrawScope.drawInvestmentsIllustration(primary: Color, surface: Color, outline: Color) {
    val w = size.width; val h = size.height

    // Background circle
    drawCircle(surface, radius = w * 0.42f, center = Offset(w / 2, h / 2))

    val chartL = w * 0.18f; val chartR = w * 0.82f; val chartB = h * 0.76f; val chartT = h * 0.24f
    val chartW = chartR - chartL; val chartH = chartB - chartT

    // Axis lines
    drawLine(outline.copy(alpha = 0.4f), Offset(chartL, chartT), Offset(chartL, chartB), 1.5.dp.toPx())
    drawLine(outline.copy(alpha = 0.4f), Offset(chartL, chartB), Offset(chartR, chartB), 1.5.dp.toPx())

    // Bars (4 bars, increasing)
    val bars = listOf(0.35f, 0.50f, 0.65f, 0.85f)
    val barW = chartW / (bars.size * 2f)
    bars.forEachIndexed { i, heightRatio ->
        val x = chartL + (i * chartW / bars.size) + barW * 0.5f
        val barH = chartH * heightRatio
        val alpha = 0.4f + 0.15f * i
        drawRoundRect(
            primary.copy(alpha = alpha),
            topLeft = Offset(x, chartB - barH),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )
    }

    // Rising trend line over bars
    val linePoints = bars.mapIndexed { i, hr ->
        val x = chartL + (i * chartW / bars.size) + barW
        val y = chartB - chartH * hr - 6.dp.toPx()
        Offset(x, y)
    }
    val linePath = Path().apply {
        moveTo(linePoints[0].x, linePoints[0].y)
        for (i in 1 until linePoints.size) {
            val cx = (linePoints[i - 1].x + linePoints[i].x) / 2f
            cubicTo(cx, linePoints[i - 1].y, cx, linePoints[i].y, linePoints[i].x, linePoints[i].y)
        }
    }
    drawPath(linePath, SemanticAmber, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    linePoints.forEach { pt ->
        drawCircle(SemanticAmber, 4.dp.toPx(), pt)
        drawCircle(Color.White.copy(alpha = 0.8f), 2.dp.toPx(), pt)
    }

    // Up arrow at top right
    val arrowX = w * 0.78f; val arrowY = h * 0.28f
    drawLine(Emerald500, Offset(arrowX, arrowY + 12.dp.toPx()), Offset(arrowX, arrowY), 2.5.dp.toPx(), StrokeCap.Round)
    drawLine(Emerald500, Offset(arrowX, arrowY), Offset(arrowX - 5.dp.toPx(), arrowY + 6.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
    drawLine(Emerald500, Offset(arrowX, arrowY), Offset(arrowX + 5.dp.toPx(), arrowY + 6.dp.toPx()), 2.5.dp.toPx(), StrokeCap.Round)
}

// ── Spending: wallet with receipt coming out ──────────────────────────────────

private fun DrawScope.drawSpendingIllustration(primary: Color, surface: Color, outline: Color) {
    val w = size.width; val h = size.height
    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

    // Background circle
    drawCircle(surface, radius = w * 0.42f, center = Offset(w / 2, h / 2))

    // Wallet body
    val wL = w * 0.20f; val wT = h * 0.35f; val wW = w * 0.52f; val wH = h * 0.36f
    drawRoundRect(primary.copy(alpha = 0.15f), Offset(wL, wT), Size(wW, wH), CornerRadius(10.dp.toPx()))
    drawRoundRect(primary.copy(alpha = 0.5f), Offset(wL, wT), Size(wW, wH), CornerRadius(10.dp.toPx()), style = stroke)

    // Wallet flap (top)
    val flapPath = Path().apply {
        moveTo(wL + 8.dp.toPx(), wT)
        lineTo(wL + wW - 8.dp.toPx(), wT)
        lineTo(wL + wW - 8.dp.toPx(), wT - h * 0.07f)
        cubicTo(wL + wW - 8.dp.toPx(), wT - h * 0.12f, wL + 8.dp.toPx(), wT - h * 0.12f, wL + 8.dp.toPx(), wT - h * 0.07f)
        close()
    }
    drawPath(flapPath, primary.copy(alpha = 0.2f))
    drawPath(flapPath, primary.copy(alpha = 0.5f), style = stroke)

    // Coin slot circle on wallet
    drawCircle(primary.copy(alpha = 0.3f), radius = w * 0.06f, center = Offset(wL + wW * 0.72f, wT + wH * 0.50f))
    drawCircle(SemanticAmber.copy(alpha = 0.8f), radius = w * 0.045f, center = Offset(wL + wW * 0.72f, wT + wH * 0.50f))

    // Receipt peeking out from right side
    val rL = wL + wW - 4.dp.toPx(); val rT = wT + h * 0.04f
    val rW = w * 0.24f; val rH = h * 0.44f
    drawRoundRect(Color.White.copy(alpha = 0.95f), Offset(rL, rT), Size(rW, rH), CornerRadius(4.dp.toPx()))
    drawRoundRect(outline.copy(alpha = 0.5f), Offset(rL, rT), Size(rW, rH), CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))

    // Lines on receipt
    val lineC = outline.copy(alpha = 0.4f)
    for (i in 0..3) {
        val lineY = rT + rH * 0.18f + i * rH * 0.17f
        val lineW = if (i % 2 == 0) rW * 0.65f else rW * 0.45f
        drawRoundRect(lineC, Offset(rL + rW * 0.15f, lineY), Size(lineW, 3.5.dp.toPx()), CornerRadius(2.dp.toPx()))
    }

    // Receipt zigzag bottom
    val zigY = rT + rH
    val zigPath = Path().apply {
        moveTo(rL, zigY)
        var x = rL
        val step = rW / 6f
        var up = true
        while (x < rL + rW) {
            x += step
            lineTo(x, if (up) zigY - 5.dp.toPx() else zigY)
            up = !up
        }
        lineTo(rL + rW, zigY)
    }
    drawPath(zigPath, outline.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
}
