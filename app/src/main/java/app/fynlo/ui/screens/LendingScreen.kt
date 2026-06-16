package app.fynlo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.PhoneEnabled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.components.CollectPaymentDialog
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendingScreen(viewModel: FinanceViewModel, onNavigateToDetail: (String) -> Unit = {}, onNavigateToCalendar: () -> Unit = {}, showHeader: Boolean = true) {
    LaunchedEffect(Unit) { app.fynlo.data.Analytics.screenView("Lending") }
    val haptic        = LocalHapticFeedback.current
    val borrowers     by viewModel.borrowers.collectAsState()
    val summary       by viewModel.financialSummary.collectAsState()
    val isPrivacy     by viewModel.isPrivacyMode.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale
    var showEmiCalc by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    // C12 Stage 3 (3.2.28) — Collect / Mark-as-Defaulted / Write-Off dialogs
    // and their state vars are gone from this screen per audit §C12 #6/#7;
    // they're now hosted in CustomerDetailScreen as proper buttons. Only the
    // Add flow (FAB → AddLendingDialog) remains on the list screen.

    // C12 Stage 2 (3.2.27) — replaced 3 filter UIs (Interest/Hand TabRow + sort
    // dropdown + collapsible Settled section) with a single Active/Overdue/Closed
    // segmented control per audit fix #3. Default is "Active" — the most
    // common state the user wants to see. Sort dropdown gone entirely
    // (audit fix #4); processed list uses a fixed sort: overdue-first then
    // by amount descending (the same default the dropdown defaulted to).
    var statusFilter by remember { mutableStateOf("Active") }
    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val processed = remember(borrowers, searchQuery) {
        val filtered = if (searchQuery.isBlank()) borrowers
                       else borrowers.filter {
                           it.name.contains(searchQuery, ignoreCase = true) ||
                           it.phone.contains(searchQuery)
                       }
        // Default sort: overdue first (most actionable), then amount desc.
        filtered.sortedWith(
            compareByDescending<Borrower> {
                it.due.isNotBlank() && it.due < today && it.paid < it.amount
            }.thenByDescending { it.amount }
        )
    }
    // Active = not settled, not written off, still has outstanding balance.
    // Hand loans (rate=0) use `paid` (old payments only updated paid, not paidPrincipal).
    // Interest loans (rate>0) use `paidPrincipal` so interest-only payments don't close the loan.
    val isActive: (app.fynlo.data.model.Borrower) -> Boolean = { b ->
        b.status !in listOf("Settled", "WrittenOff") && (
            if (b.rate <= 0) b.paid < b.amount
            else b.paidPrincipal < b.amount
        )
    }
    val activeLoans  = remember(processed) { processed.filter { isActive(it) } }
    val overdueLoans = remember(activeLoans) {
        activeLoans.filter { it.due.isNotBlank() && it.due < today }
    }
    val closedLoans  = remember(processed) { processed.filterNot { isActive(it) } }
    val displayed = when (statusFilter) {
        "Overdue" -> overdueLoans
        "Closed"  -> closedLoans
        else      -> activeLoans
    }

    if (showEmiCalc) { EmiCalculatorDialog(currencyCode = currencyCode, onDismiss = { showEmiCalc = false }) }
    if (showAddDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { borrower, source ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addBorrowerWithSource(borrower, source)
                viewModel.showFeedback("Loan added")
                showAddDialog = false
            },
            initialBorrower = null
        )
    }

    // C12 Stage 2 — back-handler for the Interest/Hand TabRow is gone with
    // the TabRow itself. The Active/Overdue/Closed segmented filter doesn't
    // need a back-stack since "Active" is the default and the user can tap
    // back to it any time.

    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) PremiumScreenHeader("Lending", "Interest loans & hand loans")
        Box(modifier = Modifier.weight(1f)) {
        app.fynlo.ui.components.PullRefresh(viewModel) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = FabBottomPadding)
        ) {
            // C12 Stage 2 — top toolbar row: EMI calculator + Calendar shortcut.
            // The stats line ("X interest · Y hand · Z settled") is gone — the
            // segmented filter below shows per-status counts, which is more
            // useful UX. The sort dropdown is gone (audit #4) — processed list
            // uses a fixed overdue-first / amount-desc sort.
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PremiumStatCard(
                            label = "Yield",
                            value = if (isPrivacy) "••••" else "${String.format(locale, "%.1f", summary.lendingYield)}%",
                            iconTint = Emerald500
                        )
                        if (!summary.lendingXirr.isNaN()) {
                            PremiumStatCard(
                                label = "XIRR",
                                value = if (isPrivacy) "••••" else app.fynlo.logic.XirrCalculator.format(summary.lendingXirr),
                                iconTint = Emerald500
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(onClick = { showEmiCalc = true }, shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                            Text("EMI", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = onNavigateToCalendar, shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Collection Calendar", Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search borrowers") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Emerald500) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        focusedBorderColor      = Color.Transparent,
                        unfocusedBorderColor    = Color.Transparent,
                        cursorColor             = Emerald500
                    )
                )
            }

            // C12 Stage 2 — Active/Overdue/Closed segmented filter. Replaces the
            // Interest/Hand TabRow (which sorted by loan-type, not status — less
            // useful for daily UX), the sort dropdown (audit #4), and the
            // collapsible Settled section (now exposed via "Closed" filter).
            item {
                val filters = listOf(
                    "Active"  to activeLoans.size,
                    "Overdue" to overdueLoans.size,
                    "Closed"  to closedLoans.size,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    filters.forEachIndexed { idx, (label, count) ->
                        SegmentedButton(
                            selected = statusFilter == label,
                            onClick = { statusFilter = label },
                            shape = SegmentedButtonDefaults.itemShape(idx, filters.size),
                            icon = {},
                            label = { Text("$label  -  $count", style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            // List of borrowers for the current filter. Filter-specific empty
            // messages make the absence of rows informative instead of just blank.
            if (displayed.isEmpty()) {
                item {
                    val msg = when (statusFilter) {
                        "Overdue" -> "No overdue loans. You're up to date."
                        "Closed"  -> "No closed loans yet"
                        else      -> if (borrowers.isEmpty()) null
                                     else "No active loans"
                    }
                    if (msg != null) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        EmptyLendingState(onAdd = { showAddDialog = true })
                    }
                }
            } else {
                itemsIndexed(displayed, key = { _, it -> it.id }) { idx, borrower ->
                    if (idx > 0) HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    LendingCard(
                        borrower     = borrower,
                        currencyCode = currencyCode,
                        isOverdue    = borrower.due.isNotBlank() && borrower.due < today && borrower.paid < borrower.amount,
                        isPrivacy    = isPrivacy,
                        onClick      = { onNavigateToDetail(borrower.id) }
                    )
                }
            }
        }
        }
    }
        }
}
@Composable
fun LendingCard(
    borrower: app.fynlo.data.model.Borrower,
    currencyCode: String = "INR",
    isOverdue: Boolean = false,
    isPrivacy: Boolean = false,
    onClick: () -> Unit,
) {
    // C12 Stage 3 (3.2.28) — rebuilt to the audit §C12 #6 spec: icon + name +
    // amount + chevron. One row tap navigates to CustomerDetailScreen, which
    // owns every per-loan action as a proper labelled button (Collect / Send
    // Reminder / Mark NPA / Write Off / Edit / Delete). The previous version
    // packed 6+ inline action callbacks plus a 70-line WhatsApp message
    // builder; all of that lifted to the detail screen so the list stays
    // scannable. Matches DebtCard visually per audit #5.
    val locale = LocalLocale.current.platformLocale
    val interest = app.fynlo.logic.InterestEngine.calcIntAccrued(
        amount = borrower.amount, rate = borrower.rate,
        loanDate = borrower.date, intType = borrower.intType,
        dueDate = borrower.due, totalPaid = borrower.paid
    )
    val outstanding = app.fynlo.logic.InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background((if (isOverdue) SemanticRed else Emerald500).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = if (isOverdue) SemanticRed else Emerald500,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    borrower.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (isOverdue) {
                    Surface(color = SemanticRed.copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            "OVERDUE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SemanticRed,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val sub = if (borrower.due.isNotBlank()) "Due ${DateUtils.formatToDisplay(borrower.due)}"
                      else "Lent ${DateUtils.formatToDisplay(borrower.date)}"
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            // C16 (3.2.41) — Outstanding on the Lent side is a receivable
            // (asset), not a debt to the user. Colour: green for normal
            // (asset), red for overdue (urgency). Pre-C16 normal state was
            // neutral onSurface — audit said it should signal asset-ness.
            val outstandingText = if (isPrivacy) "••••" else CurrencyFormatter.detail(outstanding, currencyCode, locale)
            Text(
                text = outstandingText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isOverdue) SemanticRed else Emerald500
                )
            )
            Text(
                "Outstanding",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun EmptyLendingState(onAdd: () -> Unit = {}) {
    app.fynlo.ui.components.EmptyStateIllustration(
        type        = app.fynlo.ui.components.EmptyStateType.LENDING,
        onAction    = onAdd,
        actionLabel = "Add First Loan"
    )
}
// ── EMI Calculator Dialog ─────────────────────────────────────────────────────

@Composable
fun EmiCalculatorDialog(currencyCode: String, onDismiss: () -> Unit) {
    var principal  by remember { mutableStateOf("") }
    var rate       by remember { mutableStateOf("") }
    var tenure     by remember { mutableStateOf("") }
    var dueDate    by remember { mutableStateOf("") }
    var useReducing  by remember { mutableStateOf(true) }
    var useSimple    by remember { mutableStateOf(false) }
    // useCompound = !useReducing && !useSimple (overdue scenario only)
    val locale     = LocalLocale.current.platformLocale

    // Reducing balance EMI
    val emiReducing = remember(principal, rate, tenure) {
        val p = principal.toDoubleOrNull() ?: return@remember null
        val r = (rate.toDoubleOrNull() ?: return@remember null) / 100.0 / 12.0
        val n = tenure.toIntOrNull() ?: return@remember null
        if (r == 0.0) p / n
        else p * r * Math.pow(1 + r, n.toDouble()) / (Math.pow(1 + r, n.toDouble()) - 1)
    }

    // Simple interest EMI
    val emiSimple = remember(principal, rate, tenure) {
        val p = principal.toDoubleOrNull() ?: return@remember null
        val r = rate.toDoubleOrNull() ?: return@remember null
        val n = tenure.toIntOrNull() ?: return@remember null
        val totalInterest = p * r / 100.0 * (n / 12.0)
        (p + totalInterest) / n
    }

    // Compound interest — only applies after due date (matches InterestEngine overdue rule)
    val isOverdue = remember(dueDate) {
        if (dueDate.isBlank()) false
        else runCatching {
            java.time.LocalDate.parse(dueDate, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                .isBefore(java.time.LocalDate.now())
        }.getOrDefault(false)
    }
    val emiCompound = remember(principal, rate, tenure, isOverdue) {
        if (!isOverdue) return@remember null  // only valid when overdue
        val p = principal.toDoubleOrNull() ?: return@remember null
        val r = rate.toDoubleOrNull() ?: return@remember null
        val n = tenure.toIntOrNull() ?: return@remember null
        val totalAmount = p * Math.pow(1 + r / 100.0, n / 12.0)
        totalAmount / n
    }

    val emi      = when {
        useReducing  -> emiReducing
        useSimple    -> emiSimple
        else         -> emiCompound
    }
    val total    = emi?.let { it * (tenure.toIntOrNull() ?: 0) }
    val interest = total?.let { it - (principal.toDoubleOrNull() ?: 0.0) }

    // C22 dialog universalization (3.2.55) — migrated to canonical FormDialog.
    app.fynlo.ui.components.FormDialog(
        title = "EMI Calculator",
        onDismiss = onDismiss,
    ) {
        val emiFieldColors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor      = Emerald500,
            unfocusedBorderColor    = Color.Transparent,
            focusedLabelColor       = Emerald500,
            cursorColor             = Emerald500
        )

        app.fynlo.ui.components.FormSectionLabel("Principal amount (${app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)})")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = principal, onValueChange = { principal = it },
            placeholder = { Text("0") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = emiFieldColors
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Annual interest rate (%)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = rate, onValueChange = { rate = it },
            placeholder = { Text("0.0") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = emiFieldColors
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Tenure (months)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = tenure, onValueChange = { tenure = it },
            placeholder = { Text("12") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = emiFieldColors
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Interest method")
        Spacer(Modifier.height(6.dp))
        val emiMethodOptions = listOf("Reducing", "Simple", "Compound")
        val emiSelected = when {
            useReducing -> "Reducing"
            useSimple -> "Simple"
            else -> "Compound"
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            emiMethodOptions.forEachIndexed { idx, method ->
                SegmentedButton(
                    selected = emiSelected == method,
                    onClick = {
                        useReducing = (method == "Reducing")
                        useSimple = (method == "Simple")
                    },
                    shape = SegmentedButtonDefaults.itemShape(idx, emiMethodOptions.size),
                    icon = {},
                    label = { Text(method, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        if (!useReducing && !useSimple) {
            Spacer(Modifier.height(14.dp))
            app.fynlo.ui.components.FormSectionLabel("Due date")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = dueDate,
                onValueChange = { dueDate = it },
                placeholder   = { Text("yyyy-MM-dd") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(16.dp),
                colors        = emiFieldColors
            )
            if (dueDate.isNotBlank() && !isOverdue) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Compound interest applies only after due date is exceeded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isOverdue) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Loan is overdue. Compound interest applies.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SemanticRed
                )
            }
        }

        if (emi != null) {
            val totalAmount = total ?: 0.0
            val totalInterest = interest ?: 0.0
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Monthly EMI", style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.detail(emi, currencyCode, locale),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Total Amount", style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.detail(totalAmount, currencyCode, locale), style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Total Interest", style = MaterialTheme.typography.bodyMedium)
                    Text(CurrencyFormatter.detail(totalInterest, currencyCode, locale),
                        style = MaterialTheme.typography.bodyMedium, color = SemanticRed)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Close", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
    }


