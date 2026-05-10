package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale
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
            LegendItem("Idle Cash", SemanticBlue, "${(cashWeight * 100).toInt()}%")
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
fun AccountGrowthIndicator(growth: Double, currencySymbol: String, locale: Locale) {
    val color = when {
        growth > 0 -> Emerald400
        growth < 0 -> SemanticRed
        else -> Color.Gray
    }
    val icon = when {
        growth > 0 -> Icons.Default.TrendingUp
        growth < 0 -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }
    
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(12.dp), tint = color)
        Text(
            text = if (growth == 0.0) "Stable" 
                   else "${if (growth > 0) "+" else ""}$currencySymbol${String.format(locale, "%,.0f", growth)}",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
