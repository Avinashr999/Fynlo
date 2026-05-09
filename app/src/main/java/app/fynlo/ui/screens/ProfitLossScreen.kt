package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import java.util.Locale

@Composable
fun ProfitLossScreen(viewModel: FinanceViewModel) {
    val transactions  by viewModel.transactions.collectAsState()
    val borrowers     by viewModel.borrowers.collectAsState()
    val investments   by viewModel.investments.collectAsState()
    val debts         by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val summary       by viewModel.financialSummary.collectAsState()
    val locale        = remember { Locale.getDefault() }
    val context       = androidx.compose.ui.platform.LocalContext.current

    val totalIncome    = transactions.filter { it.type.equals("income",  ignoreCase = true) }.sumOf { it.amount }
    val totalExpense   = transactions.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }
    val lendingIncome  = borrowers.sumOf { it.paid }
    val investGrowth   = investments.sumOf { it.currentVal - it.invested }
    val debtPayments   = debts.sumOf { it.paid }
    val totalRevenue   = totalIncome + lendingIncome
    val grossProfit    = totalRevenue - totalExpense
    val netProfit      = grossProfit + investGrowth - debtPayments

    fun fmt(v: Double) = "$currencySymbol ${String.format(locale, "%,.2f", v)}"
    val green = Color(0xFF059669)
    val red   = Color(0xFFEF4444)

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Profit & Loss", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Project: ${currentProject?.name ?: "Personal"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = {
                    val file = java.io.File(context.cacheDir, "pl_report_${java.time.LocalDate.now()}.pdf")
                    file.outputStream().use { app.fynlo.logic.ExportUtility.generatePDF(it, summary, transactions, borrowers, investments) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "app.fynlo.provider", file)
                    context.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"; putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, "Export P&L Report"
                    ))
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export PDF")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Net P&L card
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
            CardDefaults.cardColors((if (netProfit >= 0) green else red).copy(alpha = 0.1f))) {
            Column(Modifier.padding(20.dp)) {
                Text("Net Profit / Loss", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmt(netProfit),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (netProfit >= 0) green else red)
                Text(if (netProfit >= 0) "You are profitable" else "Expenses exceed income",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        PLSection("Revenue", listOf(
            "Transaction Income"       to totalIncome,
            "Loan Repayments Received" to lendingIncome
        ), totalRevenue, green, currencySymbol, locale)

        Spacer(Modifier.height(12.dp))

        PLSection("Expenses", listOf(
            "Total Transactions"   to totalExpense,
            "Debt Payments Made"   to debtPayments
        ), totalExpense + debtPayments, red, currencySymbol, locale)

        Spacer(Modifier.height(12.dp))

        // Net Cash Flow card
        val netCash = totalRevenue - totalExpense - debtPayments
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
            CardDefaults.cardColors((if (netCash >= 0) green else red).copy(alpha = 0.08f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Net Cash Flow", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Revenue minus all expenses", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("$currencySymbol ${String.format(locale, "%,.2f", netCash)}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (netCash >= 0) green else red)
            }
        }

        Spacer(Modifier.height(12.dp))

        PLSection("Investments", listOf(
            "Invested Amount"      to investments.sumOf { it.invested },
            "Current Market Value" to investments.sumOf { it.currentVal },
            "Unrealised Growth"    to investGrowth
        ), investGrowth, if (investGrowth >= 0) green else red, currencySymbol, locale)

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun PLSection(title: String, items: List<Pair<String, Double>>, total: Double, color: Color, currencySymbol: String, locale: Locale) {
    fun fmt(v: Double) = "$currencySymbol ${String.format(locale, "%,.2f", v)}"
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            items.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(fmt(value), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(fmt(total), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
            }
        }
    }
}
