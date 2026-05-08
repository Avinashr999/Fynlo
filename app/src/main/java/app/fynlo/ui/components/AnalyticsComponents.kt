package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SpendingAnalyticsCard(data: Map<String, Double>) {
    if (data.isEmpty()) return

    val total = data.values.sum()
    val colors = listOf(
        Color(0xFF3B82F6), Color(0xFF43A047), Color(0xFFFFB300), 
        Color(0xFFEF4444), Color(0xFF8E24AA), Color(0xFF00ACC1)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(16.dp))
            
            data.keys.toList().forEachIndexed { index, category ->
                val amount = data[category] ?: 0.0
                val percent = (amount / total * 100).toInt()
                val color = colors[index % colors.size]

                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(category, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("₹${amount.toInt()} ($percent%)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (amount / total).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.1f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }
    }
}

