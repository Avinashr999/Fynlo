package com.example.cashmemo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
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
    val filteredBorrowers = remember(borrowers, searchQuery) {
        if (searchQuery.isBlank()) borrowers
        else borrowers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.notes.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery)
        }
    }
    var editingBorrower by remember { mutableStateOf<Borrower?>(null) }
    var collectingForBorrower by remember { mutableStateOf<Borrower?>(null) }

    if (showEmiCalc) { EmiCalculatorDialog(onDismiss = { showEmiCalc = false }) }
    if (editingBorrower != null) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { editingBorrower = null },
            onConfirm = { borrower, _ ->
                viewModel.updateBorrower(borrower)
                editingBorrower = null
            },
            initialBorrower = editingBorrower
        )
    }

    if (collectingForBorrower != null) {
        CollectPaymentDialog(
            borrower = collectingForBorrower!!,
            onDismiss = { collectingForBorrower = null },
            onConfirm = { payment, dest ->
                viewModel.collectLoanPayment(payment, dest)
                collectingForBorrower = null
            }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lending Management",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                    OutlinedButton(onClick = { showEmiCalc = true }, shape = RoundedCornerShape(12.dp)) {
                        Text("EMI Calc")
                    }
                }
            }
            item {
                val suggestions = remember(searchQuery, borrowers) {
                    if (searchQuery.length < 1) emptyList()
                    else borrowers.filter { it.name.contains(searchQuery, ignoreCase = true) }.map { it.name }.distinct().take(5)
                }
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = {}) {
                    OutlinedTextField(
                        value         = searchQuery,
                        onValueChange = { searchQuery = it; expanded = true },
                        label         = { Text("Search borrowers...") },
                        leadingIcon   = { Icon(Icons.Default.Search, null) },
                        trailingIcon  = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = ""; expanded = false }) { Icon(Icons.Default.Clear, null, Modifier.size(18.dp)) } },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                        shape         = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
                        suggestions.forEach { name ->
                            DropdownMenuItem(
                                text    = { Text(name) },
                                onClick = { searchQuery = name; expanded = false }
                            )
                        }
                    }
                }
            }
            
            if (filteredBorrowers.isEmpty()) {
                item { EmptyLendingState(onAdd = { editingBorrower = Borrower("", "", "", "", "", 0.0, 0.0, "", "", 0, "Simple Interest", 0.0, "Active", "", "", 0L) }) }
            } else {
                items(filteredBorrowers) { borrower ->
                    LendingCard(
                        borrower = borrower,
                        onDelete = { viewModel.deleteBorrower(borrower) },
                        onEdit = { editingBorrower = borrower },
                        onCollect = { collectingForBorrower = borrower },
                        onClick = { onNavigateToDetail(borrower.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun LendingCard(borrower: Borrower, onDelete: () -> Unit, onEdit: () -> Unit, onCollect: () -> Unit, onClick: () -> Unit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        borrower.name, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.6f))
                    }
                    Button(
                        onClick = onCollect,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
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