package app.fynlo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import java.util.Locale

data class SearchResult(
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
    onNavigateBack: () -> Unit
) {
    val borrowers    by viewModel.borrowers.collectAsState()
    val debts        by viewModel.debts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val investments  by viewModel.investments.collectAsState()

    var query by remember { mutableStateOf("") }
    val locale = remember { Locale.getDefault() }

    val results = remember(query, borrowers, debts, transactions, investments) {
        if (query.length < 2) return@remember emptyList()
        val q = query.lowercase()
        buildList {
            borrowers.filter {
                it.name.lowercase().contains(q) || it.notes.lowercase().contains(q) || it.phone.contains(q)
            }.forEach {
                add(SearchResult(it.name, "Loan â€¢ ${it.date} â€¢ ${if (it.paid >= it.amount) "Settled" else "Active"}",
                    it.amount, "Loan", Icons.Default.Person, Color(0xFF3B82F6)))
            }
            debts.filter {
                it.name.lowercase().contains(q) || it.notes.lowercase().contains(q)
            }.forEach {
                add(SearchResult(it.name, "Debt â€¢ ${it.date} â€¢ ${if (it.paid >= it.amount) "Settled" else "Active"}",
                    it.amount, "Debt", Icons.Default.CreditCard, Color(0xFFEF4444)))
            }
            transactions.filter {
                it.desc.lowercase().contains(q) || it.category.lowercase().contains(q) ||
                it.fromAcct.lowercase().contains(q) || it.toAcct.lowercase().contains(q) ||
                it.notes.lowercase().contains(q)
            }.take(30).forEach {
                add(SearchResult(
                    it.desc.ifBlank { it.category },
                    "${it.type} â€¢ ${it.date} â€¢ ${it.category}",
                    it.amount, "Transaction",
                    if (it.type.lowercase() == "income") Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    if (it.type.lowercase() == "income") Color(0xFF059669) else Color(0xFFEF4444)
                ))
            }
            investments.filter {
                it.name.lowercase().contains(q) || it.type.lowercase().contains(q)
            }.forEach {
                add(SearchResult(it.name, "Investment â€¢ ${it.type} â€¢ ${it.date}",
                    it.currentVal, "Investment", Icons.Default.TrendingUp, Color(0xFFF59E0B)))
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
                        placeholder   = { Text("Search loans, debts, transactions...") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, null)
                                }
                            }
                        }
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
                items(results) { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier            = Modifier.padding(16.dp),
                            verticalAlignment   = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = result.color.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(result.icon, null,
                                        tint = result.color, modifier = Modifier.size(22.dp))
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(result.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(result.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("â‚¹ ${String.format(locale, "%,.0f", result.amount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = result.color)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = result.color.copy(alpha = 0.1f)
                                ) {
                                    Text(result.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = result.color,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}









