package com.example.cashmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class OnboardingPage(
    val title: String,
    val desc: String,
    val color: Color,
    val illustration: @Composable () -> Unit
)

@Composable
fun IllustrationNetWorth() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val w = size.width; val h = size.height
        val green = Color(0xFF059669); val light = Color(0xFFD1FAE5)
        // Card
        drawRoundRect(color = light, topLeft = Offset(w*0.1f,h*0.15f),
            size = Size(w*0.8f, h*0.55f), cornerRadius = CornerRadius(24f))
        drawRoundRect(color = green.copy(0.15f), topLeft = Offset(w*0.1f,h*0.15f),
            size = Size(w*0.8f, h*0.55f), cornerRadius = CornerRadius(24f),
            style = Stroke(3f))
        // Bar chart
        val bars = listOf(0.3f,0.45f,0.35f,0.6f,0.5f,0.75f,0.65f)
        val bw = w*0.06f; val gap = w*0.04f
        val baseY = h*0.6f; val maxH = h*0.3f
        bars.forEachIndexed { i, v ->
            val x = w*0.18f + i*(bw+gap)
            drawRoundRect(color = green.copy(0.3f+(i*0.1f).coerceAtMost(0.5f)),
                topLeft = Offset(x, baseY - v*maxH),
                size = Size(bw, v*maxH), cornerRadius = CornerRadius(6f))
        }
        // Trend line
        val pts = bars.mapIndexed { i, v ->
            Offset(w*0.18f + i*(bw+gap) + bw/2, baseY - v*maxH)
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color = green, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(green, 5f, it) }
        // Net worth label
        drawRoundRect(color = green, topLeft = Offset(w*0.25f, h*0.72f),
            size = Size(w*0.5f, h*0.12f), cornerRadius = CornerRadius(20f))
    }
}

@Composable
fun IllustrationLending() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val w = size.width; val h = size.height
        val blue = Color(0xFF3B82F6); val light = Color(0xFFEFF6FF)
        // Two person circles
        listOf(Offset(w*0.3f, h*0.3f), Offset(w*0.7f, h*0.3f)).forEachIndexed { i, c ->
            drawCircle(if(i==0) blue else Color(0xFF10B981), w*0.13f, c)
            drawCircle(Color.White, w*0.07f, c.copy(y = c.y - w*0.02f))
            // body arc
            drawArc(if(i==0) blue else Color(0xFF10B981),
                0f, 180f, false,
                topLeft = Offset(c.x - w*0.13f, c.y),
                size = Size(w*0.26f, w*0.2f))
        }
        // Arrow between them (money flow)
        val arrowY = h*0.32f
        drawLine(Color(0xFFFBBF24), Offset(w*0.43f, arrowY), Offset(w*0.57f, arrowY), 3f, StrokeCap.Round)
        // arrowhead
        drawPath(Path().apply {
            moveTo(w*0.57f, arrowY)
            lineTo(w*0.52f, arrowY - 8f)
            lineTo(w*0.52f, arrowY + 8f)
        }.also {}, Color(0xFFFBBF24))
        // Interest badge
        drawRoundRect(Color(0xFFFEF3C7), topLeft = Offset(w*0.3f, h*0.6f),
            size = Size(w*0.4f, h*0.14f), cornerRadius = CornerRadius(16f))
        drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(w*0.3f, h*0.6f),
            size = Size(w*0.4f, h*0.14f), cornerRadius = CornerRadius(16f), style = Stroke(2f))
    }
}

@Composable
fun IllustrationDebt() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val w = size.width; val h = size.height
        val red = Color(0xFFEF4444); val light = Color(0xFFFEF2F2)
        // Credit card
        drawRoundRect(Brush.linearGradient(listOf(red, Color(0xFFB91C1C)),
            Offset(w*0.1f, h*0.2f), Offset(w*0.9f, h*0.65f)),
            topLeft = Offset(w*0.1f, h*0.2f), size = Size(w*0.8f, h*0.45f),
            cornerRadius = CornerRadius(24f))
        // Chip
        drawRoundRect(Color(0xFFFBBF24), topLeft = Offset(w*0.18f, h*0.32f),
            size = Size(w*0.14f, w*0.1f), cornerRadius = CornerRadius(6f))
        // Card lines
        drawRoundRect(Color.White.copy(0.4f), topLeft = Offset(w*0.1f, h*0.48f),
            size = Size(w*0.8f, h*0.05f), cornerRadius = CornerRadius(0f))
        // Progress bar (payoff)
        drawRoundRect(light, topLeft = Offset(w*0.1f, h*0.74f),
            size = Size(w*0.8f, h*0.08f), cornerRadius = CornerRadius(12f))
        drawRoundRect(red.copy(0.8f), topLeft = Offset(w*0.1f, h*0.74f),
            size = Size(w*0.5f, h*0.08f), cornerRadius = CornerRadius(12f))
    }
}

@Composable
fun IllustrationInvestment() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val w = size.width; val h = size.height
        val amber = Color(0xFFF59E0B)
        // Coins stack
        listOf(0f, 0.08f, 0.16f).forEach { offset ->
            drawOval(amber.copy(0.9f - offset*2),
                topLeft = Offset(w*0.25f, h*(0.55f - offset)),
                size = Size(w*0.5f, h*0.13f))
        }
        // Growth arrow
        val path = Path().apply {
            moveTo(w*0.2f, h*0.75f)
            lineTo(w*0.4f, h*0.55f)
            lineTo(w*0.55f, h*0.62f)
            lineTo(w*0.78f, h*0.35f)
        }
        drawPath(path, Color(0xFF10B981), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Arrowhead
        drawPath(Path().apply {
            moveTo(w*0.78f, h*0.35f)
            lineTo(w*0.68f, h*0.34f)
            lineTo(w*0.78f, h*0.44f)
            close()
        }, Color(0xFF10B981))
        // Stars/sparkles
        listOf(Offset(w*0.7f, h*0.2f), Offset(w*0.2f, h*0.3f), Offset(w*0.85f, h*0.5f)).forEach {
            drawCircle(amber.copy(0.6f), 5f, it)
            drawLine(amber.copy(0.4f), it.copy(y=it.y-12f), it.copy(y=it.y+12f), 2f)
            drawLine(amber.copy(0.4f), it.copy(x=it.x-12f), it.copy(x=it.x+12f), 2f)
        }
    }
}

@Composable
fun IllustrationSync() {
    Canvas(modifier = Modifier.size(200.dp)) {
        val w = size.width; val h = size.height
        val purple = Color(0xFF8B5CF6); val light = Color(0xFFEDE9FE)
        // Cloud shape
        drawCircle(light, w*0.18f, Offset(w*0.38f, h*0.38f))
        drawCircle(light, w*0.14f, Offset(w*0.56f, h*0.40f))
        drawCircle(light, w*0.22f, Offset(w*0.50f, h*0.32f))
        drawRoundRect(light, topLeft = Offset(w*0.2f, h*0.38f), size = Size(w*0.52f, h*0.18f))
        // Cloud outline
        drawCircle(purple, w*0.18f, Offset(w*0.38f, h*0.38f), style = Stroke(2f))
        drawCircle(purple, w*0.22f, Offset(w*0.50f, h*0.32f), style = Stroke(2f))
        drawCircle(purple, w*0.14f, Offset(w*0.56f, h*0.40f), style = Stroke(2f))
        // Devices below
        listOf(Offset(w*0.25f, h*0.68f), Offset(w*0.75f, h*0.68f)).forEach { c ->
            drawRoundRect(purple.copy(0.2f), topLeft = Offset(c.x-w*0.1f, c.y-h*0.1f),
                size = Size(w*0.2f, h*0.18f), cornerRadius = CornerRadius(8f))
            drawRoundRect(purple, topLeft = Offset(c.x-w*0.1f, c.y-h*0.1f),
                size = Size(w*0.2f, h*0.18f), cornerRadius = CornerRadius(8f), style = Stroke(2f))
        }
        // Sync arrows
        drawArc(purple, 30f, 300f, false,
            topLeft = Offset(w*0.38f, h*0.56f), size = Size(w*0.24f, h*0.16f), style = Stroke(3f))
    }
}

private val pages = listOf(
    OnboardingPage(
        "Your Finance Command Centre",
        "Track net worth, loans, debts and investments — all synced to the cloud in real time.",
        Color(0xFF059669), { IllustrationNetWorth() }
    ),
    OnboardingPage(
        "Manage Lending",
        "Track money you lend to others with exact interest — Simple, Compound, or Reducing Balance.",
        Color(0xFF3B82F6), { IllustrationLending() }
    ),
    OnboardingPage(
        "Track Your Debts",
        "Know exactly what you owe, interest accrued per day, and estimated payoff at your current rate.",
        Color(0xFFEF4444), { IllustrationDebt() }
    ),
    OnboardingPage(
        "Monitor Investments",
        "Add gold, stocks, FDs and track their current value and growth over time.",
        Color(0xFFF59E0B), { IllustrationInvestment() }
    ),
    OnboardingPage(
        "Real-Time Cloud Sync",
        "Sign in with Google to sync your data across all your devices instantly.",
        Color(0xFF8B5CF6), { IllustrationSync() }
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Illustration
            Surface(
                modifier = Modifier.size(200.dp).clip(RoundedCornerShape(32.dp)),
                color    = current.color.copy(alpha = 0.10f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    current.illustration()
                }
            }

            Spacer(Modifier.height(40.dp))

            AnimatedContent(targetState = page, label = "page",
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pages[p].title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(pages[p].desc,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(40.dp))

            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == page) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == page) current.color else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                if (page > 0) {
                    TextButton(onClick = { page-- }) { Text("Back") }
                } else {
                    Spacer(Modifier.width(80.dp))
                }
                Button(
                    onClick = { if (page < pages.size - 1) page++ else onComplete() },
                    shape   = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = current.color)
                ) {
                    Text(if (page == pages.lastIndex) "Get Started" else "Next →",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onComplete) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
