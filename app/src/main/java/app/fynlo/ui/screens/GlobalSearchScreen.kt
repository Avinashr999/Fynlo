package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import java.util.Locale
import app.fynlo.ui.theme.*

data class SearchResult(
    val id: String,         // borrower.id / debt.id / transaction.id / investment.id
    val title: String,
    val subtitle: String,
    val amount: Double,
    val type: String,       // "Loan", "Debt", "Transaction", "Investment"
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBorrower: (String) -> Unit = {},
    onNavigateToCustomerDetail: (String) -> Unit = {}
) {
    val borrowers    by viewModel.borrowers.collectAsState()
    val debts        by viewModel.debts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val investments  by viewModel.investments.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"

    var query by remember { mutableStateOf("") }
    val locale = remember { Locale.getDefault() }
    val focusRequester = remember { FocusRequester() }
    // Auto-focus the search box so the keyboard opens immediately
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val results = remember(query, borrowers, debts, transactions, investments) {
        if (query.length < 2) return@remember emptyList()
        val q = query.lowercase()
        buildList {
            borrowers.filter {
                it.name.lowercase().contains(q) || it.notes.lowercase().contains(q) || it.phone.contains(q)
            }.forEach {
                add(SearchResult(
                    id       = it.id,
                    title    = it.name,
                    subtitle = "Loan • ${it.date} • ${if (it.paid >= it.amount) "Settled" else "Active"}",
                    amount   = it.amount,
                    type     = "Loan",
                    icon     = Icons.Default.Person,
                    color    = SemanticBlue
                ))
            }
            debts.filter {
                it.name.lowercase().contains(q) || it.notes.lowercase().contains(q)
            }.forEach {
                add(SearchResult(
                    id       = it.id,
                    title    = it.name,
                    subtitle = "Debt • ${it.date} • ${if (it.paid >= it.amount) "Settled" else "Active"}",
                    amount   = it.amount,
                    type     = "Debt",
                    icon     = Icons.Default.CreditCard,
                    color    = SemanticRed
                ))
            }
            transactions.filter {
                it.desc.lowercase().contains(q) || it.category.lowercase().contains(q) ||
                it.fromAcct.lowercase().contains(q) || it.toAcct.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }.take(30).forEach {
                add(SearchResult(
                    id       = it.id,
                    title    = it.desc.ifBlank { it.category },
                    subtitle = "${it.type} • ${it.date} • ${it.category}",
                    amount   = it.amount,
                    type     = "Transaction",
                    icon     = if (it.type.lowercase() == "income") Icons.Default.ArrowDownward
                               else Icons.Default.ArrowUpward,
                    color    = if (it.type.lowercase() == "income") Emerald500 else SemanticRed
                ))
            }
            investments.filter {
                it.name.lowercase().contains(q) || it.type.lowercase().contains(q)
            }.forEach {
                add(SearchResult(
                    id       = it.id,
                    title    = it.name,
                    subtitle = "Investment • ${it.type} • ${it.date}",
                    amount   = it.currentVal,
                    type     = "Investment",
                    icon     = Icons.Default.TrendingUp,
                    color    = SemanticAmber
                ))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value         = query,
                        onValueChange = { query = it },
                        placeholder   = { Text("Search loans, debts, transactions…") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        shape         = RoundedCornerShape(16.dp),
                        leadingIcon   = { Icon(Icons.Default.Search, null, tint = Emerald500) },
                        trailingIcon  = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        },
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            focusedBorderColor      = Color.Transparent,
                            unfocusedBorderColor    = Color.Transparent,
                            cursorColor             = Emerald500
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (query.length < 2) {
                item {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("Type at least 2 characters to search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (results.isEmpty()) {
                item {
                    Column(
                        modifier            = Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.SearchOff, null, Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("No results for \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text("${results.size} results for \"$query\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(results, key = { _, it -> it.type + it.id }) { idx, result ->
                    if (idx > 0) HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                when (result.type) {
                                    "Loan" -> onNavigateToCustomerDetail(result.id)
                                    else   -> { /* Debt/Transaction/Investment — back for now */ }
                                }
                            }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(result.color.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(result.icon, null, tint = result.color, modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(result.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            Text(result.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(CurrencyFormatter.detail(result.amount, currencyCode, locale),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = result.color)
                    }
                }
            }
        }
    }
}
