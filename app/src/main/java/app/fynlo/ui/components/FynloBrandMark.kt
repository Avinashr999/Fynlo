package app.fynlo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.Emerald700

@Composable
fun FynloBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Emerald700, Emerald500))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val primary = Color.White
            val secondary = Color.White.copy(alpha = 0.42f)
            val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
            drawLine(primary, Offset(w * 0.08f, h * 0.62f), Offset(w * 0.26f, h * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(primary, Offset(w * 0.26f, h * 0.46f), Offset(w * 0.42f, h * 0.56f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(primary, Offset(w * 0.42f, h * 0.56f), Offset(w * 0.62f, h * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(primary, Offset(w * 0.62f, h * 0.34f), Offset(w * 0.90f, h * 0.44f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(secondary, Offset(w * 0.10f, h * 0.76f), Offset(w * 0.40f, h * 0.70f), strokeWidth = w * 0.055f, cap = StrokeCap.Round)
            drawLine(secondary, Offset(w * 0.40f, h * 0.70f), Offset(w * 0.70f, h * 0.58f), strokeWidth = w * 0.055f, cap = StrokeCap.Round)
            drawCircle(primary, radius = w * 0.075f, center = Offset(w * 0.62f, h * 0.34f))
        }
    }
}
