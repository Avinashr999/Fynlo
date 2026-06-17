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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.FuzzyMatch
import kotlinx.coroutines.launch
import app.fynlo.ui.theme.*

/**
 * C22 (3.2.58) — search-result envelope.
 * `score` is filled by the fuzzy-match pipeline and used to sort results
 * globally so a strong hit on a Transaction outranks a weak hit on a Loan.
 */
data class SearchResult(
    val id: String,         // borrower.id / debt.id / transaction.id / investment.id
    val title: String,
    val subtitle: String,
    val amount: Double,
    val type: String,       // "Loan", "Debt", "Transaction", "Investment"
    val icon: ImageVector,
    val color: Color,
    val score: Int = 0,     // higher = more relevant
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GlobalSearchScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBorrower: (String) -> Unit = {},
    onNavigateToCustomerDetail: (String) -> Unit = {}
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val borrowers    by viewModel.borrowers.collectAsState()
    val debts        by viewModel.debts.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val investments  by viewModel.investments.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"

    val recentSearches by UserPreferences.recentSearches(context).collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    // C22 (3.2.58) — type filter chip. "All" = no narrowing. The active
    // value is kept locally (not persisted) so opening Search always lands
    // on the broad view; users can pick a bucket per session if they want.
    var typeFilter by remember { mutableStateOf("All") }
    val typeOptions = listOf("All", "Loans", "Debts", "Transactions", "Investments")

    val locale = LocalLocale.current.platformLocale
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // C22 (3.2.58) — record the query into the recents list after the user
    // stops typing for ~600ms. Avoids littering the list with every
    // intermediate prefix (e.g. "F", "Fo", "Foo", "Food").
    LaunchedEffect(query) {
        if (query.length >= 2) {
            kotlinx.coroutines.delay(600)
            UserPreferences.recordRecentSearch(context, query)
        }
    }

    val results = remember(query, typeFilter, borrowers, debts, transactions, investments) {
        if (query.length < 2) return@remember emptyList()
        buildList {
            if (typeFilter == "All" || typeFilter == "Loans") {
                borrowers.forEach { b ->
                    val s = FuzzyMatch.scoreAny(listOf(b.name, b.notes, b.phone), query)
                    if (s >= 0) add(SearchResult(
                        id = b.id, title = b.name,
                        subtitle = "Loan - ${b.date} - ${if (b.paid >= b.amount) "Settled" else "Active"}",
                        amount = b.amount, type = "Loan",
                        icon = Icons.Default.Person, color = SemanticBlue, score = s,
                    ))
                }
            }
            if (typeFilter == "All" || typeFilter == "Debts") {
                debts.forEach { d ->
                    val s = FuzzyMatch.scoreAny(listOf(d.name, d.notes), query)
                    if (s >= 0) add(SearchResult(
                        id = d.id, title = d.name,
                        subtitle = "Debt - ${d.date} - ${if (d.paid >= d.amount) "Settled" else "Active"}",
                        amount = d.amount, type = "Debt",
                        icon = Icons.Default.CreditCard, color = SemanticRed, score = s,
                    ))
                }
            }
            if (typeFilter == "All" || typeFilter == "Transactions") {
                transactions.forEach { t ->
                    val s = FuzzyMatch.scoreAny(listOf(t.desc, t.category, t.fromAcct, t.toAcct, t.notes), query)
                    if (s >= 0) add(SearchResult(
                        id = t.id, title = t.desc.ifBlank { t.category },
                        subtitle = "${t.type} - ${t.date} - ${t.category}",
                        amount = t.amount, type = "Transaction",
                        icon = if (t.type.lowercase() == "income") Icons.Default.ArrowDownward
                               else Icons.Default.ArrowUpward,
                        color = if (t.type.lowercase() == "income") Emerald500 else SemanticRed,
                        score = s,
                    ))
                }
            }
            if (typeFilter == "All" || typeFilter == "Investments") {
                investments.forEach { i ->
                    val s = FuzzyMatch.scoreAny(listOf(i.name, i.type), query)
                    if (s >= 0) add(SearchResult(
                        id = i.id, title = i.name,
                        subtitle = "Investment - ${i.type} - ${i.date}",
                        amount = i.currentVal, type = "Investment",
                        icon = Icons.Default.TrendingUp, color = SemanticAmber, score = s,
                    ))
                }
            }
        }
            // Global sort: a high-scoring transaction outranks a low-scoring
            // loan. Within ties, alpha by title for determinism.
            .sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.title.lowercase() })
            // Cap result list — anything past 60 is noise the user won't scroll to.
            .take(60)
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(76.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(21.dp))
                        }
                    }
                    OutlinedTextField(
                        value         = query,
                        onValueChange = { query = it },
                        placeholder   = { Text("Search loans, debts, transactions") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f).focusRequester(focusRequester),
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
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // C22 (3.2.58) — type filter row. Always visible so users can
            // pre-narrow before typing OR re-narrow after seeing too many
            // results. FlowRow handles wrapping on narrow screens.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                typeOptions.forEach { opt ->
                    TemplatePill(
                        text = opt,
                        selected = typeFilter == opt,
                        onClick = { typeFilter = opt },
                    )
                }
            }

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (query.length < 2) {
                    // C22 (3.2.58) — recent-searches chips replace the
                    // bare "type 2 chars" empty state. Users tap a chip
                    // to re-run the query; the chip row hides on first
                    // install (empty recents) so the original CTA stays.
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Recent searches",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = {
                                    scope.launch { UserPreferences.clearRecentSearches(context) }
                                }) {
                                    Text("Clear", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement   = Arrangement.spacedBy(4.dp),
                            ) {
                                recentSearches.forEach { q ->
                                    AssistChip(
                                        onClick = { query = q },
                                        label   = { Text(q, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(Icons.Default.History, null,
                                                modifier = Modifier.size(14.dp))
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Column(
                                modifier            = Modifier.fillMaxWidth().padding(top = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Search, null, Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f))
                                Spacer(Modifier.height(16.dp))
                                Text("Search across Fynlo",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(6.dp))
                                Text("Type at least 2 characters to find loans, debts, transactions, and investments.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface)
                            if (typeFilter != "All") {
                                Spacer(Modifier.height(8.dp))
                                Text("Try widening the type filter above.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = { typeFilter = "All" }) {
                                    Text("Search all")
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text("Check spelling or try a shorter word.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                } else {
                    item {
                        Text("${app.fynlo.logic.pluralize(results.size, "result")} for \"$query\"",
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
}
