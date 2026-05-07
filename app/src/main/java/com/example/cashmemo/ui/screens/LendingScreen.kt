package com.example.cashmemo.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sort
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.PhoneEnabled
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
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.logic.DateUtils
import com.example.cashmemo.logic.InterestEngine
import com.example.cashmemo.ui.components.AddLendingDialog
import com.example.cashmemo.ui.components.CollectPaymentDialog
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendingScreen(viewModel: FinanceViewModel, onNavigateToDetail: (String) -> Unit = {}) {
    val borrowers by viewModel.borrowers.collectAsState()
    var showEmiCalc by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy      by remember { mutableStateOf("Overdue") } // Overdue, Amount, Name, Date
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBorrower by remember { mutableStateOf<Borrower?>(null) }
    var collectingForBorrower by remember { mutableStateOf<Borrower?>(null) }
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
    val activeLoans  = processed.filter { it.status != "Settled" && it.paid < it.amount }
    val settledLoans = processed.filter { it.status == "Settled" || it.paid >= it.amount }

    if (showEmiCalc) { EmiCalculatorDialog(onDismiss = { showEmiCalc = false }) }
    if (showAddDialog || editingBorrower != null) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { editingBorrower = null; showAddDialog = false },
            onConfirm = { borrower, source ->
                if (editingBorrower != null) viewModel.updateBorrower(borrower)
                else viewModel.addBorrowerWithSource(borrower, source)
                editingBorrower = null; showAddDialog = false
            },
            initialBorrower = editingBorrower
        )
    }
    if (collectingForBorrower != null) {
        CollectPaymentDialog(
            borrower  = collectingForBorrower!!,
            onDismiss = { collectingForBorrower = null },
            onConfirm = { payment, dest ->
                viewModel.collectLoanPayment(payment, dest)
                collectingForBorrower = null
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // Header
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Lending", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${activeLoans.size} active • ${settledLoans.size} settled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            OutlinedButton(onClick = { showSortMenu = true }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Sort, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(sortBy)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                listOf("Overdue", "Amount", "Name", "Date").forEach { opt ->
                                    DropdownMenuItem(text = { Text(opt) }, onClick = { sortBy = opt; showSortMenu = false })
                                }
                            }
                        }
                        OutlinedButton(onClick = { showEmiCalc = true }, shape = RoundedCornerShape(12.dp)) {
                            Text("EMI")
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
                        CardDefaults.cardColors(Color(0xFF3B82F6).copy(alpha = 0.1f))) {
                        Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                            Column {
                                Text("Total Outstanding", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${String.format(java.util.Locale.getDefault(), "%,.0f", totalOut)}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF3B82F6))
                            }
                            if (overdueCount > 0) {
                                Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                    Text("$overdueCount OVERDUE",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Active loans
            if (activeLoans.isEmpty() && settledLoans.isEmpty()) {
                item { EmptyLendingState(onAdd = { showAddDialog = true }) }
            } else {
                if (activeLoans.isNotEmpty()) {
                    item {
                        Text("Active Loans", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    items(activeLoans) { borrower ->
                        LendingCard(
                            borrower  = borrower,
                            isOverdue = borrower.due.isNotBlank() && borrower.due < today,
                            onDelete  = { viewModel.deleteBorrower(borrower) },
                            onEdit    = { editingBorrower = borrower },
                            onCollect = { collectingForBorrower = borrower },
                            onClick   = { onNavigateToDetail(borrower.id) }
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
fun LendingCard(borrower: Borrower, isOverdue: Boolean = false, onDelete: () -> Unit, onEdit: () -> Unit, onCollect: () -> Unit, onClick: () -> Unit) {
    // Pulsing animation for overdue
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f, label = "pulseAlpha",
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Person, null,
                        tint = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                    Text(borrower.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (isOverdue) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = pulseAlpha * 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("OVERDUE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = LocalContext.current
                    if (borrower.phone.isNotBlank()) {
                        // WhatsApp reminder
                        val outstanding = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)
                        val phone = borrower.phone.trim().replace(" ","").replace("-","")
                        // Add country code if not present
                        val intlPhone = when {
                            phone.startsWith("+") -> phone
                            phone.startsWith("91") && phone.length == 12 -> "+$phone"
                            phone.length == 10 -> "+91$phone"
                            else -> phone
                        }
                        val dueStr = if (borrower.due.isNotBlank()) " due on ${borrower.due}" else ""
                        val waMsg  = "Hi ${borrower.name}, this is a friendly reminder that your outstanding loan balance is ₹${String.format(locale, "%,.0f", outstanding)}$dueStr. Kindly arrange repayment at your earliest convenience. Thank you! - Cash Memo"
                        val smsMsg = "Hi ${borrower.name}, outstanding: ₹${String.format(locale, "%,.0f", outstanding)}$dueStr. Please repay. -Cash Memo"

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
                            Icon(Icons.Default.Message, "WhatsApp", Modifier.size(22.dp), tint = Color(0xFF25D366))
                        }
                        IconButton(onClick = {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_SENDTO,
                                    android.net.Uri.parse("smsto:$intlPhone")
                                ).apply { putExtra("sms_body", smsMsg) }
                            )
                        }) {
                            Icon(Icons.Default.Sms, "SMS", Modifier.size(22.dp), tint = Color(0xFF3B82F6))
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
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", Modifier.size(20.dp), tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.6f))
                    }
                    Button(onClick = onCollect, contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)) {
                        Text("Collect Pay", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹ ${String.format(Locale.getDefault(), "%,.0f", borrower.amount)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Lent: ${DateUtils.formatToDisplay(borrower.date)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    
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
                    Text("₹ ${String.format(Locale.getDefault(), "%,.0f", interest)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF388E3C)))
                    Text(borrower.type, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            // For "Both" type show SI + CI split
            if (bothPortions != null) {
                Spacer(Modifier.height(6.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ SI (until due date)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF388E3C))
                            Text("₹ ${String.format(locale, "%,.0f", bothPortions.first)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF388E3C))
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("➔ CI (after due date)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFEF4444))
                            Text("₹ ${String.format(locale, "%,.0f", bothPortions.second)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFEF4444))
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
                        Text("₹ ${String.format(locale, "%,.2f", perDayInterest)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF388E3C))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Paid So Far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹ ${String.format(locale, "%,.0f", borrower.paid)}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
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
                        "₹ ${String.format(Locale.getDefault(), "%,.0f", outs)}", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                    )
                }
            }

            if (borrower.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Person, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text("No active loans", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Track money you've lent to friends or family.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add First Loan")
        }
    }
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
                            color = Color(0xFFEF4444)
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
                                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
