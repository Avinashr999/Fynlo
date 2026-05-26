package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.InterestEngine
import java.util.Locale
import kotlin.math.ceil
import app.fynlo.ui.theme.*

@Composable
fun DebtPayoffScreen(viewModel: FinanceViewModel) {
    val debts  by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = remember { Locale.getDefault() }

    val activeDebts = debts.filter { it.status != "Cleared" && it.amount > it.paid }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Debt Payoff Tracker",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp))

        if (activeDebts.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No active debts!", style = MaterialTheme.typography.titleLarge, color = Emerald500)
                    Text("You are debt-free.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val totalOwed = activeDebts.sumOf { d ->
                val interest = InterestEngine.calcIntAccrued(d.amount, d.rate, d.date, d.intType, d.due, d.paid)
                d.amount + interest - d.paid
            }
            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
                Text("Total Remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.detail(totalOwed, currencyCode, locale),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = SemanticRed)
                Text("Across ${activeDebts.size} active debt(s)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                itemsIndexed(activeDebts, key = { _, d -> d.id }) { index, debt ->
                    DebtPayoffCard(debt, currencyCode, locale)
                    if (index < activeDebts.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtPayoffCard(debt: Debt, currencyCode: String, locale: Locale) {
    val interest    = InterestEngine.calcIntAccrued(debt.amount, debt.rate, debt.date, debt.intType, debt.due, debt.paid)
    val outstanding = (debt.amount + interest - debt.paid).coerceAtLeast(0.0)
    val monthlyRate = debt.rate / 100.0 / 12.0

    val today = java.time.LocalDate.now()
    val loanDate = runCatching { java.time.LocalDate.parse(debt.date, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }.getOrDefault(today)
    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(loanDate, today).toDouble().coerceAtLeast(1.0)
    val monthsElapsed = daysBetween / 30.0
    val avgMonthlyPayment = if (monthsElapsed > 0) debt.paid / monthsElapsed else 0.0

    val monthsRemaining = when {
        avgMonthlyPayment <= 0 -> null
        monthlyRate == 0.0     -> ceil(outstanding / avgMonthlyPayment).toInt()
        else -> {
            val ratio = monthlyRate * outstanding / avgMonthlyPayment
            if (ratio >= 1.0) null
            else ceil(-Math.log(1.0 - ratio) / Math.log(1.0 + monthlyRate)).toInt()
        }
    }

    val progress = if (debt.amount > 0) (debt.paid / (debt.amount + interest)).toFloat().coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(debt.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Surface(color = SemanticRed.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
                    Text(CurrencyFormatter.detail(outstanding, currencyCode, locale),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SemanticRed, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                color = Emerald500)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${(progress * 100).toInt()}% paid", style = MaterialTheme.typography.labelSmall, color = Emerald500)
                Text("${CurrencyFormatter.detail(debt.paid, currencyCode, locale)} paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
                InfoPill("Rate", "${debt.rate}%", Modifier.weight(1f))
                InfoPill("Interest", CurrencyFormatter.detail(interest, currencyCode, locale), Modifier.weight(1f))
                if (monthsRemaining != null) {
                    InfoPill("Est. Payoff", "$monthsRemaining mo", Modifier.weight(1f))
                } else {
                    InfoPill("Avg/Month", if (avgMonthlyPayment > 0) CurrencyFormatter.detail(avgMonthlyPayment, currencyCode, locale) else "No payments yet", Modifier.weight(1f))
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
