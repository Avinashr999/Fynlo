package app.fynlo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.components.CollectPaymentDialog
import java.util.Locale
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendingScreen(viewModel: FinanceViewModel, onNavigateToDetail: (String) -> Unit = {}, onNavigateToCalendar: () -> Unit = {}) {
    val haptic        = LocalHapticFeedback.current
    val borrowers     by viewModel.borrowers.collectAsState()
    val accounts      by viewModel.accounts.collectAsState()
    val people        by viewModel.people.collectAsState()  // for phone lookup
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    var showEmiCalc by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy      by remember { mutableStateOf("Overdue") } // Overdue, Amount, Name, Date
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBorrower by remember { mutableStateOf<Borrower?>(null) }
    var collectingForBorrower by remember { mutableStateOf<Borrower?>(null) }
    var defaultingBorrower by remember { mutableStateOf<Borrower?>(null) }
    var writeOffBorrower by remember { mutableStateOf<Borrower?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Interest Loans 1=Hand Loans
    val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val processed = remember(borrowers, searchQuery, sortBy) {
        var list = if (searchQuery.isBlank()) borrowers
                   else borrowers.filter {
                       it.name.contains(searchQuery, ignoreCase = true) ||
                       it.phone.contains(searchQuery)
                   }
        list = when (sortBy) {
            "Amount"  -> list.sortedByDescending { it.amount }
            "Name"    -> list.sortedBy { it.name }
            "Date"    -> list.sortedBy { it.date }
            else      -> list.sortedWith(compareByDescending<Borrower> {
                it.due.isNotBlank() && it.due < today && it.paid < it.amount
            }.thenByDescending { it.amount })
        }
        list
    }
    // Active = not settled, not written off, still has outstanding balance
    // Hand loans (rate=0): use `paid` because old payments only updated paid, not paidPrincipal
    // Interest loans (rate>0): use `paidPrincipal` so interest-only payments don't close the loan
    val isActive: (app.fynlo.data.model.Borrower) -> Boolean = { b ->
        b.status !in listOf("Settled", "WrittenOff") && (
            if (b.rate <= 0) b.paid < b.amount          // hand loan: fully collected = settled
            else b.paidPrincipal < b.amount              // interest loan: principal not yet recovered
        )
    }
    val interestLoans  = processed.filter { it.rate > 0  && isActive(it) }
    val handLoans      = processed.filter { it.rate <= 0 && isActive(it) }
    val defaultedLoans = processed.filter { it.status == "Defaulted" }
    // Settled = marked Settled/WrittenOff, OR fully paid (using same logic as isActive)
    val settledLoans   = processed.filter { b ->
        !isActive(b) && b.status !in listOf("Defaulted", "WrittenOff")
    }
    val activeLoans    = if (selectedTab == 0) interestLoans else handLoans

    if (showEmiCalc) { EmiCalculatorDialog(onDismiss = { showEmiCalc = false }) }
    if (showAddDialog || editingBorrower != null) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { editingBorrower = null; showAddDialog = false },
            onConfirm = { borrower, source ->
                if (editingBorrower != null) viewModel.updateBorrower(borrower)
                else viewModel.addBorrowerWithSource(borrower, source)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); editingBorrower = null; showAddDialog = false
            },
            initialBorrower = editingBorrower
        )
    }
    if (collectingForBorrower != null) {
        CollectPaymentDialog(
            borrower  = collectingForBorrower!!,
            accounts  = accounts,
            onDismiss = { collectingForBorrower = null },
            onConfirm = { payment, dest ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.collectLoanPayment(payment, dest)
                collectingForBorrower = null
            }
        )
    }

    // Mark as Defaulted / Restore to Active dialog (same dialog, toggles based on current status)
    if (defaultingBorrower != null) {
        val b = defaultingBorrower!!
        val isCurrentlyDefaulted = b.status == "Defaulted"
        AlertDialog(
            onDismissRequest = { defaultingBorrower = null },
            title = { Text(if (isCurrentlyDefaulted) "Restore to Performing?" else "Mark as Defaulted?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${b.name}  •  ₹${String.format(java.util.Locale.getDefault(), "%,.0f", b.amount)}")
                    Text(
                        if (isCurrentlyDefaulted)
                            "This will mark the borrower as Active again and unfreeze interest accrual from today."
                        else
                            "Interest will be frozen at today's value. The borrower will be marked NPA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (isCurrentlyDefaulted) viewModel.restoreBorrowerToActive(b)
                        else viewModel.markBorrowerDefaulted(b)
                        defaultingBorrower = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentlyDefaulted) Emerald500 else SemanticAmber)
                ) {
                    Text(if (isCurrentlyDefaulted) "Restore to Active" else "Mark as NPA")
                }
            },
            dismissButton = { TextButton(onClick = { defaultingBorrower = null }) { Text("Cancel") } }
        )
    }

    // Write-off confirmation
    if (writeOffBorrower != null) {
        val b = writeOffBorrower!!
        AlertDialog(
            onDismissRequest = { writeOffBorrower = null },
            title = { Text("Write Off Bad Debt?") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${b.name}  •  ₹${String.format(java.util.Locale.getDefault(), "%,.0f", b.amount)}")
                    Text("This will create a Bad Debt expense in your P&L and remove the borrower from receivables. Cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.writeOffBorrower(b)
                    writeOffBorrower = null
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Write Off")
                }
            },
            dismissButton = { TextButton(onClick = { writeOffBorrower = null }) { Text("Cancel") } }
        )
    }

    // Back gesture: if on Hand Loans tab, go back to Interest Loans tab first
    androidx.activity.compose.BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // Header
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        PremiumScreenHeader("Lending", "Interest loans & hand loans")
                        Text("${interestLoans.size} interest • ${handLoans.size} hand loans • ${settledLoans.size} settled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Sort
                        Box {
                            OutlinedButton(onClick = { showSortMenu = true }, shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(Icons.Default.Sort, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(sortBy, style = MaterialTheme.typography.labelMedium)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                listOf("Overdue", "Amount", "Name", "Date").forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = { sortBy = opt; showSortMenu = false })
                                }
                            }
                        }
                        // EMI calculator
                        OutlinedButton(onClick = { showEmiCalc = true }, shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("EMI", style = MaterialTheme.typography.labelMedium)
                        }
                        // Calendar — icon only to save space
                        OutlinedButton(onClick = onNavigateToCalendar, shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Collection Calendar", Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Search
            item {
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search borrowers...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }

            // Summary card
            if (activeLoans.isNotEmpty()) {
                item {
                    val totalOut = activeLoans.sumOf {
                        val interest = InterestEngine.calcIntAccrued(it.amount, it.rate, it.date, it.type, it.due, it.paid)
                        InterestEngine.calcOutstanding(it.amount, interest, it.paid)
                    }
                    val overdueCount = activeLoans.count { it.due.isNotBlank() && it.due < today }
                    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp),
                        CardDefaults.cardColors(SemanticBlue.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Outstanding", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", totalOut)}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = SemanticBlue)
                            }
                            if (overdueCount > 0) {
                                Surface(color = SemanticRed.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                    Text("$overdueCount OVERDUE",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = SemanticRed,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Active loans
            val hasAnyLoans = interestLoans.isNotEmpty() || handLoans.isNotEmpty() || settledLoans.isNotEmpty() || defaultedLoans.isNotEmpty()

            if (!hasAnyLoans) {
                item { EmptyLendingState(onAdd = { showAddDialog = true }) }
            } else {
                // Always show tabs whenever any loans exist
                item {
                    TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surface) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                            Column(Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Interest Loans", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${interestLoans.size}", style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                            Column(Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("Hand Loans", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("${handLoans.size}", style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (activeLoans.isEmpty()) {
                    // Current tab is empty — show friendly message
                    item {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(
                                if (selectedTab == 0) "No interest loans" else "No hand loans",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(activeLoans) { borrower ->
                        LendingCard(
                            borrower  = borrower,
                            people    = people,
                            currencySymbol = currencySymbol,
                            isOverdue = borrower.due.isNotBlank() && borrower.due < today,
                            onDelete  = { viewModel.deleteBorrower(borrower) },
                            onEdit    = { editingBorrower = borrower },
                            onCollect = { collectingForBorrower = borrower },
                            onClick   = { onNavigateToDetail(borrower.id) },
                            onDefault = { defaultingBorrower = borrower },
                            onWriteOff = { writeOffBorrower = borrower }
                        )
                    }
                }

                // Settled section
                if (settledLoans.isNotEmpty()) {
                    item {
                        var showSettled by remember { mutableStateOf(false) }
                        Column {
                            Row(Modifier.fillMaxWidth().clickable { showSettled = !showSettled }
                                .padding(vertical = 8.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("Settled (${settledLoans.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(if (showSettled) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (showSettled) {
                                settledLoans.forEach { borrower ->
                                    LendingCard(
                                        borrower  = borrower,
                                        people    = people,
                                        currencySymbol = currencySymbol,
                                        isOverdue = false,
                                        onDelete  = { viewModel.deleteBorrower(borrower) },
                                        onEdit    = { editingBorrower = borrower },
                                        onCollect = { collectingForBorrower = borrower },
                                        onClick   = { onNavigateToDetail(borrower.id) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // FAB
        androidx.compose.material3.FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, null) }
    }
}
@Composable
fun LendingCard(borrower: Borrower, people: List<app.fynlo.data.model.Person> = emptyList(), currencySymbol: String = "₹", isOverdue: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit, onCollect: () -> Unit, onClick: () -> Unit, onDefault: () -> Unit = {}, onWriteOff: () -> Unit = {}) {
    // Pulsing animation for overdue
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f, label = "pulseAlpha",
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    // Phone: use borrower.phone first, fall back to matching person in contact book
    val phone = borrower.phone.ifBlank {
        people.find { it.name.equals(borrower.name, ignoreCase = true) }?.phone ?: ""
    }
    // Normalize to international format
    val intlPhone = remember(phone) {
        val p = phone.trim().replace(" ", "").replace("-", "")
        when {
            p.startsWith("+") -> p          // already has country code
            p.startsWith("91") && p.length == 12 -> "+$p"
            p.length == 10 -> "+91$p"       // default to India if 10 digits
            else -> p
        }
    }
    val interest = InterestEngine.calcIntAccrued(
        amount = borrower.amount,
        rate = borrower.rate,
        loanDate = borrower.date,
        intType = borrower.type,
        dueDate = borrower.due,
        totalPaid = borrower.paid
    )
    val outs     = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)
    val daysElapsed = InterestEngine.daysBetween(borrower.date, java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    val perDayInterest = if (daysElapsed > 0) interest / daysElapsed else 0.0
    val locale   = Locale.getDefault()
    // For "Both" type — show SI and CI portions separately
    val bothPortions = if (borrower.type == "Both") InterestEngine.calcBothPortions(
        borrower.amount, borrower.rate, borrower.date, borrower.due, borrower.paid
    ) else null
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null,
                        tint = if (isOverdue) SemanticRed else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Text(borrower.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (isOverdue) {
                        Surface(
                            color = SemanticRed.copy(alpha = pulseAlpha * 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("OVERDUE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SemanticRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    if (phone.isNotBlank()) {
                        val outstanding = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)
                        val today       = java.time.LocalDate.now()
                        val todayStr    = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                        // ── Days overdue (only if due date passed) ──────────────────
                        val daysOverdue: Long = if (borrower.due.isNotBlank() && borrower.due < todayStr) {
                            InterestEngine.daysBetween(borrower.due, todayStr)
                        } else 0L

                        // ── Days since loan started ─────────────────────────────────
                        val daysSince = InterestEngine.daysBetween(borrower.date, todayStr)

                        // ── Build smart WhatsApp message ────────────────────────────
                        val waMsg = buildString {
                            appendLine("Hi ${borrower.name},")
                            appendLine()

                            if (daysOverdue > 0) {
                                // Overdue path — more urgent, shows exact days late
                                appendLine("This is a reminder that your loan repayment is *${daysOverdue} day${if (daysOverdue != 1L) "s" else ""} overdue*.")
                                appendLine()
                                appendLine("*Loan Summary:*")
                                appendLine("• Principal: ${currencySymbol}${String.format(locale, "%,.0f", borrower.amount)}")
                                if (borrower.rate > 0) {
                                    appendLine("• Interest accrued (${borrower.rate}% ${borrower.type}): ${currencySymbol}${String.format(locale, "%,.0f", interest)}")
                                }
                                if (borrower.paid > 0) {
                                    appendLine("• Amount paid so far: ${currencySymbol}${String.format(locale, "%,.0f", borrower.paid)}")
                                }
                                appendLine("• *Total outstanding: ${currencySymbol}${String.format(locale, "%,.0f", outstanding)}*")
                                appendLine()
                                append("Please arrange payment at your earliest. ")
                                if (borrower.rate > 0) {
                                    val dailyRate = borrower.amount * borrower.rate / 36500.0
                                    append("Interest is adding ~${currencySymbol}${String.format(locale, "%.0f", dailyRate)}/day.")
                                }
                            } else {
                                // Not overdue — friendly reminder
                                val dueInfo = if (borrower.due.isNotBlank()) {
                                    val daysLeft = InterestEngine.daysBetween(todayStr, borrower.due)
                                    " (due in ${daysLeft} day${if (daysLeft != 1L) "s" else ""})"
                                } else ""
                                appendLine("This is a friendly reminder about your outstanding loan${dueInfo}.")
                                appendLine()
                                appendLine("*Loan Summary:*")
                                appendLine("• Principal: ${currencySymbol}${String.format(locale, "%,.0f", borrower.amount)}")
                                if (borrower.rate > 0) {
                                    appendLine("• Interest so far (${daysSince} days @ ${borrower.rate}%): ${currencySymbol}${String.format(locale, "%,.0f", interest)}")
                                }
                                if (borrower.paid > 0) {
                                    appendLine("• Paid: ${currencySymbol}${String.format(locale, "%,.0f", borrower.paid)}")
                                }
                                appendLine("• *Outstanding: ${currencySymbol}${String.format(locale, "%,.0f", outstanding)}*")
                                appendLine()
                                append("Kindly arrange repayment at your convenience. Thank you!")
                            }
                            appendLine()
                            append("— Fynlo")
                        }.trimEnd()

                        // SMS is shorter — just the essentials
                        val smsMsg = buildString {
                            if (daysOverdue > 0) {
                                append("Hi ${borrower.name}, your loan of ${currencySymbol}${String.format(locale, "%,.0f", borrower.amount)} is ${daysOverdue} days overdue. ")
                            } else {
                                append("Hi ${borrower.name}, loan reminder: ")
                            }
                            append("Outstanding: ${currencySymbol}${String.format(locale, "%,.0f", outstanding)}")
                            if (borrower.rate > 0) append(" (incl. interest)")
                            append(". Please repay soon. -Fynlo")
                        }

                        IconButton(onClick = {
                            val uri = android.net.Uri.parse("https://wa.me/$intlPhone?text=${android.net.Uri.encode(waMsg)}")
                            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                            catch (e: Exception) {
                                context.startActivity(android.content.Intent.createChooser(
                                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, waMsg)
                                    }, "Send Reminder"
                                ))
                            }
                        }) {
                            // WhatsApp branded button — green bubble with phone icon
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.size(22.dp)
                                    .background(Color(0xFF25D366), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Call, null, Modifier.size(13.dp), tint = Color.White)
                            }
                        }
                        IconButton(onClick = {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO,
                                    android.net.Uri.parse("smsto:$intlPhone")
                                ).apply { putExtra("sms_body", smsMsg) }
                            )
                        }) {
                            Icon(Icons.Default.Sms, "SMS", Modifier.size(22.dp), tint = SemanticBlue)
                        }
                    } else {
                        // No phone — show clear hint
                        TextButton(
                            onClick = onEdit,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PhoneEnabled, null, Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("Add Phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = {
                        // Share loan summary as text
                        val outstanding = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)
                        val summary = buildString {
                            appendLine("=== Loan Summary ===")
                            appendLine("Borrower : ${borrower.name}")
                            appendLine("Principal: $currencySymbol${String.format(locale, "%,.2f", borrower.amount)}")
                            appendLine("Interest : ${borrower.rate}% (${borrower.type})")
                            appendLine("Lent On  : ${borrower.date}")
                            if (borrower.due.isNotBlank()) appendLine("Due Date : ${borrower.due}")
                            appendLine("Paid     : $currencySymbol${String.format(locale, "%,.2f", borrower.paid)}")
                            appendLine("Interest : $currencySymbol${String.format(locale, "%,.2f", interest)}")
                            appendLine("Outstanding: $currencySymbol${String.format(locale, "%,.2f", outstanding)}")
                            appendLine()
                            appendLine("Generated by Fynlo App")
                        }
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Loan Summary - ${borrower.name}")
                                putExtra(android.content.Intent.EXTRA_TEXT, summary)
                            }, "Share Loan Summary"
                        ))
                    }) {
                        Icon(Icons.Default.Sms, "SMS", Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "Edit", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), tint = SemanticRed.copy(alpha = 0.7f))
                    }


                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", borrower.amount)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lent: ${DateUtils.formatToDisplay(borrower.date)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        if (borrower.sourceAccount.isNotBlank()) {
                            Text("  •  ${borrower.sourceAccount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    if (borrower.due.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("DUE: ${DateUtils.formatToDisplay(borrower.due)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Interest (${borrower.rate}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", interest)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Emerald500))
                    Text(borrower.type, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            // For "Both" type show SI + CI split
            if (bothPortions != null) {
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ SI (until due date)", style = MaterialTheme.typography.labelSmall, color = Emerald500)
                            Text("$currencySymbol ${String.format(locale, "%,.0f", bothPortions.first)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Emerald500)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ CI (after due date)", style = MaterialTheme.typography.labelSmall, color = SemanticRed)
                            Text("$currencySymbol ${String.format(locale, "%,.0f", bothPortions.second)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = SemanticRed)
                        }
                    }
                }
            }

            // Days elapsed + per day interest
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Days Elapsed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$daysElapsed days", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Per Day Interest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol ${String.format(locale, "%,.2f", perDayInterest)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Emerald500)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paid So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$currencySymbol ${String.format(locale, "%,.0f", borrower.paid)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column {
                    Text("Total Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", outs)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                    )
                }
            }

            // Collect Payment button + NPA toggle row
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onCollect,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.MonetizationOn, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Collect Payment", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(4.dp))
            // NPA toggle button
            if (borrower.status == "Defaulted") {
                OutlinedButton(
                    onClick = onDefault,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald500),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("NPA — Tap to Restore Active", style = MaterialTheme.typography.labelSmall)
                }
            } else {
                TextButton(
                    onClick = onDefault,
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = SemanticAmber)
                    Spacer(Modifier.width(4.dp))
                    Text("Mark as Defaulted / NPA",
                        style = MaterialTheme.typography.labelSmall,
                        color = SemanticAmber)
                }
            }

            if (borrower.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes, 
                            contentDescription = null, 
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            borrower.notes, 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
fun EmiCalculatorDialog(onDismiss: () -> Unit) {
    var principal  by remember { mutableStateOf("") }
    var rate       by remember { mutableStateOf("") }
    var tenure     by remember { mutableStateOf("") }
    var dueDate    by remember { mutableStateOf("") }
    var useReducing  by remember { mutableStateOf(true) }
    var useSimple    by remember { mutableStateOf(false) }
    // useCompound = !useReducing && !useSimple (overdue scenario only)
    val locale     = remember { java.util.Locale.getDefault() }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("EMI Calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = principal, onValueChange = { principal = it },
                    label = { Text("Principal Amount (₹)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = rate, onValueChange = { rate = it },
                    label = { Text("Annual Interest Rate (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = tenure, onValueChange = { tenure = it },
                    label = { Text("Tenure (months)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                // Method toggle
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = useReducing,
                        onClick  = { useReducing = true; useSimple = false },
                        label    = { Text("Reducing", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = useSimple,
                        onClick  = { useReducing = false; useSimple = true },
                        label    = { Text("Simple", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !useReducing && !useSimple,
                        onClick  = { useReducing = false; useSimple = false },
                        label    = { Text("Compound", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Show due date field only for Compound mode
                if (!useReducing && !useSimple) {
                    OutlinedTextField(
                        value         = dueDate,
                        onValueChange = { dueDate = it },
                        label         = { Text("Due Date (yyyy-MM-dd)") },
                        placeholder   = { Text("e.g. 2024-12-31") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp)
                    )
                    if (dueDate.isNotBlank() && !isOverdue) {
                        Text(
                            "Compound interest applies only after due date is exceeded.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOverdue) {
                        Text(
                            "Loan is overdue — compound interest applies.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SemanticRed
                        )
                    }
                }

                if (emi != null) {
                    HorizontalDivider()
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Monthly EMI", style = MaterialTheme.typography.bodyMedium)
                                Text("₹ ${String.format(locale, "%,.2f", emi)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Total Amount", style = MaterialTheme.typography.bodyMedium)
                                Text("₹ ${String.format(locale, "%,.2f", total!!)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Total Interest", style = MaterialTheme.typography.bodyMedium)
                                Text("₹ ${String.format(locale, "%,.2f", interest!!)}",
                                    style = MaterialTheme.typography.bodyMedium, color = SemanticRed)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}









