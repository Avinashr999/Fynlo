package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Debt
import app.fynlo.logic.InterestEngine
import java.util.Locale
import kotlin.math.ceil

@Composable
fun DebtPayoffScreen(viewModel: FinanceViewModel) {
    val debts  by viewModel.debts.collectAsState()
    val locale = remember { Locale.getDefault() }

    val activeDebts = debts.filter { it.status != "Cleared" && it.amount > it.paid }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Text("Debt Payoff Tracker",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp))

        if (activeDebts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No active debts!", style = MaterialTheme.typography.titleLarge, color = Color(0xFF059669))
                    Text("You are debt-free.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Total summary
            val totalOwed = activeDebts.sumOf { d ->
                val interest = InterestEngine.calcIntAccrued(d.amount, d.rate, d.date, d.intType, d.due, d.paid)
                d.amount + interest - d.paid
            }
            Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), RoundedCornerShape(16.dp),
                CardDefaults.cardColors(Color(0xFFFFEBEE).copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total Remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹ ${String.format(locale, "%,.2f", totalOwed)}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color(0xFFEF4444))
                    Text("Across ${activeDebts.size} active debt(s)", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)) {
                items(activeDebts) { debt ->
                    DebtPayoffCard(debt, locale)
                }
            }
        }
    }
}

@Composable
private fun DebtPayoffCard(debt: Debt, locale: Locale) {
    val interest    = InterestEngine.calcIntAccrued(debt.amount, debt.rate, debt.date, debt.intType, debt.due, debt.paid)
    val outstanding = (debt.amount + interest - debt.paid).coerceAtLeast(0.0)
    val monthlyRate = debt.rate / 100.0 / 12.0

    // Estimate months remaining based on average monthly payment so far
    val today = java.time.LocalDate.now()
    val loanDate = runCatching { java.time.LocalDate.parse(debt.date, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(today)
    val monthsElapsed = ((java.time.temporal.ChronoUnit.DAYS.between(loanDate, today)) / 30.0).coerceAtLeast(1.0)
    val avgMonthlyPayment = if (monthsElapsed > 0) debt.paid / monthsElapsed else 0.0

    val monthsRemaining = when {
        avgMonthlyPayment <= 0 -> null
        monthlyRate == 0.0     -> ceil(outstanding / avgMonthlyPayment).toInt()
        else -> {
            // n = -log(1 - (r * P) / EMI) / log(1 + r)
            val ratio = monthlyRate * outstanding / avgMonthlyPayment
            if (ratio >= 1.0) null
            else ceil(-Math.log(1.0 - ratio) / Math.log(1.0 + monthlyRate)).toInt()
        }
    }

    val progress = if (debt.amount > 0) (debt.paid / (debt.amount + interest)).toFloat().coerceIn(0f, 1f) else 0f

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(debt.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                    Text("₹ ${String.format(locale, "%,.0f", outstanding)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                color = Color(0xFF059669))
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}% paid", style = MaterialTheme.typography.labelSmall, color = Color(0xFF059669))
                Text("₹ ${String.format(locale, "%,.0f", debt.paid)} paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                InfoPill("Rate", "${debt.rate}%", Modifier.weight(1f))
                InfoPill("Interest", "₹${String.format(locale, "%,.0f", interest)}", Modifier.weight(1f))
                if (monthsRemaining != null) {
                    InfoPill("Est. Payoff", "$monthsRemaining mo", Modifier.weight(1f))
                } else {
                    InfoPill("Avg/Month", if (avgMonthlyPayment > 0) "₹${String.format(locale, "%,.0f", avgMonthlyPayment)}" else "No payments yet", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String, modifier: Modifier) {
    Surface(modifier, RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}








