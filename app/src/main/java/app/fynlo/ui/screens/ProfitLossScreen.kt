package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import app.fynlo.ui.theme.*

@Composable
fun ProfitLossScreen(viewModel: FinanceViewModel) {
    val transactions   by viewModel.transactions.collectAsState()
    val borrowers      by viewModel.borrowers.collectAsState()
    val investments    by viewModel.investments.collectAsState()
    val debts          by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol  = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val summary        by viewModel.financialSummary.collectAsState()
    val locale          = remember { Locale.getDefault() }
    val context         = androidx.compose.ui.platform.LocalContext.current

    // ── Cash-basis figures — exclude financing activities (debt/lending/investment)
    // Debt received = liability (not income), Debt repayment principal = balance sheet (not expense)
    val financingCategories = listOf("Debt Received", "Debt Repayment", "Lending", "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns")
    val cashTxns       = transactions.filter { it.tags != "journal_only" && it.category !in financingCategories }
    val totalIncome    = cashTxns.filter { it.type.equals("income",  ignoreCase = true) }.sumOf { it.amount }
    val totalExpense   = cashTxns.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }

    // ── Interest tracking ───────────────────────────────────────────────────
    // Use paidInterest from borrowers — this is the ACTUAL interest collected
    // NOT the full Loan Repayment transaction (which includes principal recovery)
    val interestIncome   = borrowers.sumOf { it.paidInterest }
    val interestExpense  = transactions.filter { it.category == "Interest Expense" }.sumOf { it.amount }  // journal entries
    val badDebtWriteOffs = transactions.filter { it.category == "Bad Debt" }.sumOf { it.amount }          // journal entries

    // ── Other metrics ───────────────────────────────────────────────────────
    // Unrealised growth = current value vs remaining cost basis (invested minus already withdrawn)
    // If you invest 50k and withdraw 10k: remaining basis = 40k, current = 40k → growth = 0
    val investGrowth     = investments.sumOf { it.currentVal - (it.invested - it.withdrawn) }
    val investReturns    = transactions.filter { it.category == "Investment Returns" }.sumOf { it.amount }
    val principalIncome  = totalIncome - interestIncome  // non-interest income
    val operatingExpense = totalExpense - transactions.filter {
        it.category in listOf("Debt Repayment", "Lending", "Investment") && it.tags != "journal_only"
    }.sumOf { it.amount }

    // ── P&L ────────────────────────────────────────────────────────────────
    val grossRevenue     = totalIncome + investReturns
    val operatingProfit  = grossRevenue - totalExpense
    val netProfit        = operatingProfit - interestExpense - badDebtWriteOffs

    fun fmt(v: Double, showSign: Boolean = false): String {
        val sign = if (showSign && v > 0) "+" else ""
        return "$sign$currencySymbol ${String.format(locale, "%,.0f", v)}"
    }
    val green = Emerald500
    val red   = SemanticRed
    val amber = SemanticAmber

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Profit & Loss", "Revenue, expenses & lending P&L")
        Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {

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

        // ── Net P&L hero card — premium dark emerald ──────────────────────────
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
            CardDefaults.cardColors(if (netProfit >= 0) Emerald700 else red)) {
            Box {
                Box(Modifier.size(100.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f)).align(Alignment.TopEnd))
                Column(Modifier.padding(22.dp)) {
                    Text("Net Profit / Loss", style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Text(fmt(netProfit),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold, color = Color.White))
                    Text(if (netProfit >= 0) "You are profitable ↑" else "Expenses exceed income ↓",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── REVENUE ───────────────────────────────────────────────────────────
        PLSection("Revenue", listOf(
            "Interest Income (collected)"  to interestIncome,
            "Other Income"                 to principalIncome,
            "Investment Returns"           to investReturns
        ), grossRevenue, green, currencySymbol, locale)

        Spacer(Modifier.height(12.dp))

        // ── EXPENSES ──────────────────────────────────────────────────────────
        PLSection("Expenses", listOf(
            "Business / Personal Expenses" to totalExpense,
            "Interest Paid (Cost of Debt)" to interestExpense,
            "Bad Debt Write-offs"          to badDebtWriteOffs
        ), totalExpense + interestExpense + badDebtWriteOffs, red, currencySymbol, locale)

        Spacer(Modifier.height(12.dp))

        // ── LENDING PERFORMANCE ───────────────────────────────────────────────
        val activeBorrowers = borrowers.filter { it.status != "WrittenOff" }
        val totalLent        = activeBorrowers.sumOf { it.amount }
        val totalRecovered   = activeBorrowers.sumOf { it.paidPrincipal }
        val interestCollected = activeBorrowers.sumOf { it.paidInterest }
        val defaultedAmt     = borrowers.filter { it.status == "Defaulted" || it.status == "WrittenOff" }.sumOf { it.amount - it.paidPrincipal }

        PLSection("Lending Business", listOf(
            "Total Lent Out"          to totalLent,
            "Principal Recovered"     to totalRecovered,
            "Interest Collected"      to interestCollected,
            "Bad / Defaulted Loans"   to -defaultedAmt
        ), interestCollected - badDebtWriteOffs, green, currencySymbol, locale)

        Spacer(Modifier.height(12.dp))

        // ── INVESTMENTS ───────────────────────────────────────────────────────
        // Investments P&L = only gains/returns, NOT the capital itself (balance sheet)
        PLSection("Investments", listOf(
            "Realised Returns (withdrawn)"  to investReturns,
            "Unrealised Growth (on paper)"  to investGrowth.coerceAtLeast(0.0),
            "Unrealised Loss (on paper)"    to investGrowth.coerceAtMost(0.0)
        ), investGrowth + investReturns, if (investGrowth + investReturns >= 0) green else red, currencySymbol, locale)

        Spacer(Modifier.height(100.dp))
    }
    }
}

@Composable
private fun PLSection(
    title: String,
    items: List<Pair<String, Double>>,
    total: Double,
    color: Color,
    currencySymbol: String,
    locale: Locale
) {
    fun fmt(v: Double) = "$currencySymbol ${String.format(locale, "%,.0f", v)}"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Section header with colored left bar
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.width(3.dp).height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = color))
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { (label, value) ->
                val valueColor = when {
                    value < 0 -> SemanticRed
                    label.contains("Interest Income") || label.contains("Collected") || label.contains("Returned") || label.contains("Growth") -> Emerald500
                    label.contains("Interest Paid") || label.contains("Bad Debt") || label.contains("Loss") -> SemanticRed
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Text(fmt(value),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = valueColor)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = color.copy(alpha = 0.15f))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Net", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.1f)) {
                    Text(fmt(total),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold, color = color),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}
