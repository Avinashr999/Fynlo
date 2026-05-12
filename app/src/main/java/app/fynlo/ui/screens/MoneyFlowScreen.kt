package app.fynlo.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import app.fynlo.logic.ExportUtility
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Transaction
import java.util.Locale
import app.fynlo.ui.theme.*

data class FlowEntry(
    val from: String,
    val to: String,
    val amount: Double,
    val label: String,
    val date: String,
    val flowType: FlowType
)

enum class FlowType {
    INCOME, EXPENSE, TRANSFER, LENDING, DEBT_REPAY, DEBT_RECEIVED, INVESTMENT
}

@Composable
fun MoneyFlowScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val borrowers    by viewModel.borrowers.collectAsState()
    val debts        by viewModel.debts.collectAsState()
    val accounts     by viewModel.allAccountsUnfiltered.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    val locale       = remember { Locale.getDefault() }
    val context      = LocalContext.current
    var showExportMenu by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Income", "Expense", "Transfer", "Lending", "Debt")

    val allFlows = remember(transactions, borrowers, debts) {
        val flows = mutableListOf<FlowEntry>()

        transactions.forEach { t ->
            when (t.type.lowercase()) {
                "income" -> flows.add(FlowEntry(
                    from     = t.category.ifBlank { "External" },
                    to       = t.toAcct.ifBlank { "Unknown" },
                    amount   = t.amount,
                    label    = t.desc.ifBlank { t.category },
                    date     = t.date,
                    flowType = FlowType.INCOME
                ))
                "expense" -> flows.add(FlowEntry(
                    from     = t.fromAcct.ifBlank { "Unknown" },
                    to       = t.category.ifBlank { "Expense" },
                    amount   = t.amount,
                    label    = t.desc.ifBlank { t.category },
                    date     = t.date,
                    flowType = FlowType.EXPENSE
                ))
                "transfer" -> flows.add(FlowEntry(
                    from     = t.fromAcct.ifBlank { "Unknown" },
                    to       = t.toAcct.ifBlank { "Unknown" },
                    amount   = t.amount,
                    label    = "Transfer",
                    date     = t.date,
                    flowType = FlowType.TRANSFER
                ))
                "investment" -> flows.add(FlowEntry(
                    from     = t.fromAcct.ifBlank { "Account" },
                    to       = t.toAcct.ifBlank { t.category },
                    amount   = t.amount,
                    label    = t.desc.ifBlank { "Investment" },
                    date     = t.date,
                    flowType = FlowType.INVESTMENT
                ))
            }
        }

        borrowers.forEach { b ->
            flows.add(FlowEntry(
                from     = "Your Account",
                to       = b.name,
                amount   = b.amount,
                label    = "Lent to ${b.name}",
                date     = b.date,
                flowType = FlowType.LENDING
            ))
            if (b.paid > 0) {
                flows.add(FlowEntry(
                    from     = b.name,
                    to       = "Your Account",
                    amount   = b.paid,
                    label    = "Repayment from ${b.name}",
                    date     = b.date,
                    flowType = FlowType.INCOME
                ))
            }
        }

        debts.forEach { d ->
            flows.add(FlowEntry(
                from     = d.name,
                to       = "Your Account",
                amount   = d.amount,
                label    = "Borrowed from ${d.name}",
                date     = d.date,
                flowType = FlowType.DEBT_RECEIVED
            ))
            if (d.paid > 0) {
                flows.add(FlowEntry(
                    from     = "Your Account",
                    to       = d.name,
                    amount   = d.paid,
                    label    = "Repaid to ${d.name}",
                    date     = d.date,
                    flowType = FlowType.DEBT_REPAY
                ))
            }
        }

        flows.sortedByDescending { it.date }
    }

    val filteredFlows = remember(allFlows, selectedTab) {
        when (selectedTab) {
            1 -> allFlows.filter { it.flowType == FlowType.INCOME }
            2 -> allFlows.filter { it.flowType == FlowType.EXPENSE }
            3 -> allFlows.filter { it.flowType == FlowType.TRANSFER || it.flowType == FlowType.INVESTMENT }
            4 -> allFlows.filter { it.flowType == FlowType.LENDING }
            5 -> allFlows.filter { it.flowType == FlowType.DEBT_RECEIVED || it.flowType == FlowType.DEBT_REPAY }
            else -> allFlows
        }
    }

    val accountFlows = remember(allFlows, accounts) {
        val map = mutableMapOf<String, Pair<Double, Double>>()
        accounts.forEach { map[it.name] = Pair(0.0, 0.0) }
        allFlows.forEach { flow ->
            val inPrev  = map[flow.to]   ?: Pair(0.0, 0.0)
            val outPrev = map[flow.from] ?: Pair(0.0, 0.0)
            map[flow.to]   = Pair(inPrev.first + flow.amount,  inPrev.second)
            map[flow.from] = Pair(outPrev.first, outPrev.second + flow.amount)
        }
        map.entries.sortedByDescending { it.value.first + it.value.second }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Money Flow", "Income & expense patterns")

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Money Flow", style = MaterialTheme.typography.labelSmall)  // placeholder
                    Text("Track every movement in your currency", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    FilledTonalButton(onClick = { showExportMenu = true }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export")
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export as CSV") },
                            leadingIcon = { Icon(Icons.Default.TableChart, null) },
                            onClick = {
                                showExportMenu = false
                                val csv  = ExportUtility.generateMoneyFlowCSV(allFlows)
                                val file = File(context.cacheDir, "money_flow_${java.time.LocalDate.now()}.csv")
                                file.writeText(csv)
                                val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share Money Flow CSV"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                            onClick = {
                                showExportMenu = false
                                val file = File(context.cacheDir, "money_flow_${java.time.LocalDate.now()}.pdf")
                                file.outputStream().use { ExportUtility.generateMoneyFlowPDF(it, allFlows) }
                                val uri  = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }, "Share Money Flow PDF"))
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Text("Account Flows", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                accountFlows.take(5).forEach { (name, flows) ->
                    AccountFlowCard(name, flows.first, flows.second, currencySymbol, locale)
                    Spacer(Modifier.height(6.dp))
                }
            }

            item {
                val totalIn  = allFlows.filter { it.flowType == FlowType.INCOME || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
                val totalOut = allFlows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY }.sumOf { it.amount }
                val lentOut  = allFlows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Flow Summary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        HorizontalDivider()
                        FlowSummaryRow("Total Inflow",   "$currencySymbol${String.format(locale, "%,.0f", totalIn)}",  Emerald500)
                        FlowSummaryRow("Total Outflow",  "$currencySymbol${String.format(locale, "%,.0f", totalOut)}", SemanticRed)
                        FlowSummaryRow("Lent Out",       "$currencySymbol${String.format(locale, "%,.0f", lentOut)}",  SemanticAmber)
                        HorizontalDivider()
                        FlowSummaryRow("Net Flow",
                            "$currencySymbol${String.format(locale, "%,.0f", totalIn - totalOut)}",
                            if (totalIn >= totalOut) Emerald500 else SemanticRed,
                            bold = true)
                    }
                }
            }

            item {
                Text("Transaction Flows", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tabs.size, key = { it }) { i ->
                        FilterChip(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            label    = { Text(tabs[i], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (filteredFlows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text("No flows found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredFlows, key = { "${it.date}_${it.label}_${it.amount}" }) { flow ->
                    FlowEntryCard(flow, currencySymbol, locale)
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun AccountFlowCard(name: String, inflow: Double, outflow: Double, currencySymbol: String, locale: Locale) {
    val net = inflow - outflow
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Net: $currencySymbol${String.format(locale, "%,.0f", net)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (net >= 0) Emerald500 else SemanticRed)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("▲ $currencySymbol${String.format(locale, "%,.0f", inflow)}", fontSize = 11.sp, color = Emerald500)
                Text("▼ $currencySymbol${String.format(locale, "%,.0f", outflow)}", fontSize = 11.sp, color = SemanticRed)
            }
        }
    }
}

@Composable
private fun FlowEntryCard(flow: FlowEntry, currencySymbol: String, locale: Locale) {
    val (bgColor, arrowColor, icon) = when (flow.flowType) {
        FlowType.INCOME        -> Triple(Emerald500.copy(0.08f), Emerald500, Icons.Default.ArrowDownward)
        FlowType.EXPENSE       -> Triple(SemanticRed.copy(0.08f), SemanticRed, Icons.Default.ArrowUpward)
        FlowType.TRANSFER      -> Triple(SemanticBlue.copy(0.08f), SemanticBlue, Icons.AutoMirrored.Filled.ArrowForward)
        FlowType.LENDING       -> Triple(SemanticAmber.copy(0.08f), SemanticAmber, Icons.Default.Person)
        FlowType.DEBT_RECEIVED -> Triple(Carbon500.copy(0.08f), Carbon500, Icons.Default.CreditCard)
        FlowType.DEBT_REPAY    -> Triple(SemanticRed.copy(0.08f), SemanticRed, Icons.Default.CreditCard)
        FlowType.INVESTMENT    -> Triple(Color(0xFF06B6D4).copy(0.08f), Color(0xFF06B6D4), Icons.Default.TrendingUp)
    }

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = bgColor)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(36.dp), RoundedCornerShape(8.dp), color = arrowColor.copy(0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = arrowColor)
                }
            }
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(flow.from, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(flow.to, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = arrowColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, false))
                }
                Text(flow.label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(flow.date, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(Modifier.width(8.dp))

            Text("$currencySymbol${String.format(locale, "%,.0f", flow.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = arrowColor)
        }
    }
}

@Composable
private fun FlowSummaryRow(label: String, value: String, color: Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold)
            else MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}
