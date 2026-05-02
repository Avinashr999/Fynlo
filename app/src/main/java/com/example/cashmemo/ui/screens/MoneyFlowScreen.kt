package com.example.cashmemo.ui.screens

import androidx.compose.foundation.background
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
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.data.model.Debt
import com.example.cashmemo.data.model.Transaction
import java.util.Locale

// ── Data model for a single flow entry ───────────────────────────────────────
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
    val locale       = remember { Locale.getDefault() }

    // Selected filter tab
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Income", "Expense", "Transfer", "Lending", "Debt")

    // Build all flow entries
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

    // Filtered flows
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

    // Account net flow map
    val accountFlows = remember(allFlows, accounts) {
        val map = mutableMapOf<String, Pair<Double, Double>>() // name -> (inflow, outflow)
        accounts.forEach { map[it.name] = Pair(0.0, 0.0) }
        allFlows.forEach { flow ->
            val inPrev  = map[flow.to]   ?: Pair(0.0, 0.0)
            val outPrev = map[flow.from] ?: Pair(0.0, 0.0)
            map[flow.to]   = Pair(inPrev.first + flow.amount,  inPrev.second)
            map[flow.from] = Pair(outPrev.first, outPrev.second + flow.amount)
        }
        map.entries.sortedByDescending { it.value.first + it.value.second }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

        // Header
        Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)) {
            Text("Money Flow", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold))
            Text("Track every rupee movement", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Account summary cards
            item {
                Text("Account Flows", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                accountFlows.take(5).forEach { (name, flows) ->
                    AccountFlowCard(name, flows.first, flows.second, locale)
                    Spacer(Modifier.height(6.dp))
                }
            }

            // Total summary
            item {
                val totalIn  = allFlows.filter { it.flowType == FlowType.INCOME || it.flowType == FlowType.DEBT_RECEIVED }.sumOf { it.amount }
                val totalOut = allFlows.filter { it.flowType == FlowType.EXPENSE || it.flowType == FlowType.DEBT_REPAY }.sumOf { it.amount }
                val lentOut  = allFlows.filter { it.flowType == FlowType.LENDING }.sumOf { it.amount }

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                    CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Flow Summary", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        HorizontalDivider()
                        FlowSummaryRow("Total Inflow",   "₹${String.format(locale, "%,.0f", totalIn)}",  Color(0xFF10B981))
                        FlowSummaryRow("Total Outflow",  "₹${String.format(locale, "%,.0f", totalOut)}", Color(0xFFEF4444))
                        FlowSummaryRow("Lent Out",       "₹${String.format(locale, "%,.0f", lentOut)}",  Color(0xFFF59E0B))
                        HorizontalDivider()
                        FlowSummaryRow("Net Flow",
                            "₹${String.format(locale, "%,.0f", totalIn - totalOut)}",
                            if (totalIn >= totalOut) Color(0xFF10B981) else Color(0xFFEF4444),
                            bold = true)
                    }
                }
            }

            // Filter chips
            item {
                Text("Transaction Flows", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tabs.size) { i ->
                        FilterChip(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            label    = { Text(tabs[i], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Flow entries
            if (filteredFlows.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text("No flows found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(filteredFlows) { flow ->
                    FlowEntryCard(flow, locale)
                }
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun AccountFlowCard(name: String, inflow: Double, outflow: Double, locale: Locale) {
    val net = inflow - outflow
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Net: ₹${String.format(locale, "%,.0f", net)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (net >= 0) Color(0xFF10B981) else Color(0xFFEF4444))
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("▲ ₹${String.format(locale, "%,.0f", inflow)}", fontSize = 11.sp, color = Color(0xFF10B981))
                Text("▼ ₹${String.format(locale, "%,.0f", outflow)}", fontSize = 11.sp, color = Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun FlowEntryCard(flow: FlowEntry, locale: Locale) {
    val (bgColor, arrowColor, icon) = when (flow.flowType) {
        FlowType.INCOME        -> Triple(Color(0xFF10B981).copy(0.08f), Color(0xFF10B981), Icons.Default.ArrowDownward)
        FlowType.EXPENSE       -> Triple(Color(0xFFEF4444).copy(0.08f), Color(0xFFEF4444), Icons.Default.ArrowUpward)
        FlowType.TRANSFER      -> Triple(Color(0xFF3B82F6).copy(0.08f), Color(0xFF3B82F6), Icons.AutoMirrored.Filled.ArrowForward)
        FlowType.LENDING       -> Triple(Color(0xFFF59E0B).copy(0.08f), Color(0xFFF59E0B), Icons.Default.Person)
        FlowType.DEBT_RECEIVED -> Triple(Color(0xFF8B5CF6).copy(0.08f), Color(0xFF8B5CF6), Icons.Default.CreditCard)
        FlowType.DEBT_REPAY    -> Triple(Color(0xFFEF4444).copy(0.08f), Color(0xFFEF4444), Icons.Default.CreditCard)
        FlowType.INVESTMENT    -> Triple(Color(0xFF06B6D4).copy(0.08f), Color(0xFF06B6D4), Icons.Default.TrendingUp)
    }

    Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
        CardDefaults.cardColors(containerColor = bgColor)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Icon
            Surface(Modifier.size(36.dp), RoundedCornerShape(10.dp), color = arrowColor.copy(0.15f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = arrowColor)
                }
            }
            Spacer(Modifier.width(10.dp))

            // From → To
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

            // Amount
            Text("₹${String.format(locale, "%,.0f", flow.amount)}",
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
