package app.fynlo.ui.screens

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import app.fynlo.logic.ExportUtility
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import app.fynlo.logic.CurrencyFormatter
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
    val currencyCode = currentProject?.currency ?: "INR"
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
            Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
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
                                val csv  = ExportUtility.generateMoneyFlowCSV(allFlows, currencyCode)
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
                                file.outputStream().use { ExportUtility.generateMoneyFlowPDF(it, allFlows, currencyCode) }
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
                accountFlows.take(5).forEachIndexed { index, (name, flows) ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                    AccountFlowCard(name, flows.first, flows.second, currencyCode, locale)
                }
            }

            item {
                val totalIn  = allFlows.filter { it.flowType == FlowType.INCOME || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
                val totalOut = allFlows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY }.sumOf { it.amount }
                val lentOut  = allFlows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }

                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Flow Summary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    HorizontalDivider()
                    FlowSummaryRow("Total Inflow",   CurrencyFormatter.detail(totalIn, currencyCode, locale),  Emerald500)
                    FlowSummaryRow("Total Outflow",  CurrencyFormatter.detail(totalOut, currencyCode, locale), SemanticRed)
                    FlowSummaryRow("Lent Out",       CurrencyFormatter.detail(lentOut, currencyCode, locale),  SemanticAmber)
                    HorizontalDivider()
                    FlowSummaryRow("Net Flow",
                        if (totalIn - totalOut < 0) CurrencyFormatter.negative(totalIn - totalOut, currencyCode, locale)
                        else CurrencyFormatter.detail(totalIn - totalOut, currencyCode, locale),
                        if (totalIn >= totalOut) Emerald500 else SemanticRed,
                        bold = true)
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
                itemsIndexed(filteredFlows, key = { _, it -> "${it.date}_${it.label}_${it.amount}" }) { index, flow ->
                    if (index > 0) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                    FlowEntryCard(flow, currencyCode, locale)
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun AccountFlowCard(name: String, inflow: Double, outflow: Double, currencyCode: String, locale: Locale) {
    val net = inflow - outflow
    Row(Modifier.padding(vertical = 12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Net: ${if (net < 0) CurrencyFormatter.negative(net, currencyCode, locale) else CurrencyFormatter.detail(net, currencyCode, locale)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (net >= 0) Emerald500 else SemanticRed)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("▲ ${CurrencyFormatter.detail(inflow, currencyCode, locale)}", fontSize = 11.sp, color = Emerald500)
            Text("▼ ${CurrencyFormatter.detail(outflow, currencyCode, locale)}", fontSize = 11.sp, color = SemanticRed)
        }
    }
}

@Composable
private fun FlowEntryCard(flow: FlowEntry, currencyCode: String, locale: Locale) {
    val (arrowColor, icon) = when (flow.flowType) {
        FlowType.INCOME        -> Pair(Emerald500, Icons.Default.ArrowDownward)
        FlowType.EXPENSE       -> Pair(SemanticRed, Icons.Default.ArrowUpward)
        FlowType.TRANSFER      -> Pair(SemanticBlue, Icons.AutoMirrored.Filled.ArrowForward)
        FlowType.LENDING       -> Pair(SemanticAmber, Icons.Default.Person)
        FlowType.DEBT_RECEIVED -> Pair(Carbon500, Icons.Default.CreditCard)
        FlowType.DEBT_REPAY    -> Pair(SemanticRed, Icons.Default.CreditCard)
        FlowType.INVESTMENT    -> Pair(SemanticTeal, Icons.Default.TrendingUp)
    }

    Row(Modifier.padding(vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

        Text(CurrencyFormatter.detail(flow.amount, currencyCode, locale),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = arrowColor)
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
