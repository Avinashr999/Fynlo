package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.theme.*

@Composable
fun WealthDistributionBar(
    cash: Double,
    investments: Double,
    interestLoans: Double,
    handLoans: Double
) {
    val total = cash + investments + interestLoans + handLoans
    if (total <= 0) return

    val cashWeight = (cash / total).toFloat()
    val growingWeight = ((investments + interestLoans) / total).toFloat()
    val handLoanWeight = (handLoans / total).toFloat()

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "Wealth Distribution",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
        ) {
            if (cashWeight > 0.01) Box(Modifier.fillMaxHeight().weight(cashWeight).background(SemanticBlue))
            if (growingWeight > 0.01) Box(Modifier.fillMaxHeight().weight(growingWeight).background(SemanticAmber))
            if (handLoanWeight > 0.01) Box(Modifier.fillMaxHeight().weight(handLoanWeight).background(Carbon400))
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LegendItem("Office & Petty Cash", SemanticBlue, "${(cashWeight * 100).toInt()}%")
            LegendItem("Growing Assets", SemanticAmber, "${(growingWeight * 100).toInt()}%")
            LegendItem("Hand Loans", Carbon400, "${(handLoanWeight * 100).toInt()}%")
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, percent: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(text = "$label ($percent)", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun AccountGrowthIndicator(growth: Double, currencyCode: String = "INR", locale: Locale = Locale.getDefault()) {
    // 3.2.65 — neutral case was Color.Gray (fixed mid-grey, invisible
    // against Carbon800 dark surface). onSurfaceVariant adapts per theme.
    val neutralTint = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when {
        growth > 0 -> Emerald400
        growth < 0 -> SemanticRed
        else -> neutralTint
    }
    val icon = when {
        growth > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        growth < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(
            text = when {
                growth == 0.0 -> "Stable"
                growth < 0     -> CurrencyFormatter.negative(growth, currencyCode, locale)
                else           -> "+${CurrencyFormatter.detail(growth, currencyCode, locale)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
