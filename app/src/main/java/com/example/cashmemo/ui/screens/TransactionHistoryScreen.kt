package com.example.cashmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Transaction
import com.example.cashmemo.logic.DateUtils
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(viewModel: FinanceViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var selectedType   by remember { mutableStateOf("All") }
    var showDateFilter by remember { mutableStateOf(false) }
    var fromDate       by remember { mutableStateOf("") }
    var toDate         by remember { mutableStateOf("") }
    val types = listOf("All", "Income", "Expense")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Master History",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )
        
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            types.forEach { type ->
                FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(type) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showDateFilter = !showDateFilter }) {
                Icon(Icons.Default.DateRange, null,
                    tint = if (showDateFilter || fromDate.isNotBlank()) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Date range filter
        if (showDateFilter) {
            val today = java.time.LocalDate.now()
            val displayFmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quick preset chips
                    Text("Quick Select", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "Today"      to (today to today),
                            "Yesterday"  to (today.minusDays(1) to today.minusDays(1)),
                            "Last 7d"    to (today.minusDays(6) to today),
                            "Last 30d"   to (today.minusDays(29) to today),
                            "This Month" to (today.withDayOfMonth(1) to today),
                            "Last Month" to (today.minusMonths(1).withDayOfMonth(1) to today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())),
                            "This Year"  to (today.withDayOfYear(1) to today)
                        ).forEach { (label, range) ->
                            FilterChip(
                                selected = fromDate == range.first.format(displayFmt) && toDate == range.second.format(displayFmt),
                                onClick  = { fromDate = range.first.format(displayFmt); toDate = range.second.format(displayFmt) },
                                label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    HorizontalDivider()
                    // Manual date pickers
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.example.cashmemo.ui.components.DatePickerField(
                            value = fromDate, onValueChange = { fromDate = it },
                            label = "From", optional = true, modifier = Modifier.weight(1f)
                        )
                        com.example.cashmemo.ui.components.DatePickerField(
                            value = toDate, onValueChange = { toDate = it },
                            label = "To", optional = true, modifier = Modifier.weight(1f)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (fromDate.isNotBlank() || toDate.isNotBlank()) {
                            TextButton(onClick = { fromDate = ""; toDate = "" }) { Text("Clear") }
                        }
                        Button(onClick = { showDateFilter = false }, modifier = Modifier.height(36.dp)) {
                            Text("Apply")
                        }
                    }
                }
            }
        }

        val filteredHistory = remember(transactions, selectedType, fromDate, toDate) {
            var list = if (selectedType == "All") transactions
                       else transactions.filter { it.type.equals(selectedType, ignoreCase = true) }
            // Date range filter (convert DD-MM-YYYY to YYYY-MM-DD for comparison)
            if (fromDate.isNotBlank()) {
                val from = runCatching {
                    val d = java.time.LocalDate.parse(fromDate, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }.getOrDefault("")
                if (from.isNotBlank()) list = list.filter { it.date >= from }
            }
            if (toDate.isNotBlank()) {
                val to = runCatching {
                    val d = java.time.LocalDate.parse(toDate, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }.getOrDefault("")
                if (to.isNotBlank()) list = list.filter { it.date <= to }
            }
            list
        }

        if (filteredHistory.isEmpty()) {
            EmptyTransactionState()
        } else {
            // Group by Date for better readability
            val grouped = filteredHistory.groupBy { it.date }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                grouped.keys.sortedByDescending { it }.forEach { date ->
                    item {
                        Text(
                            text = DateUtils.formatToDisplay(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(grouped[date] ?: emptyList()) { transaction ->
                    TransactionItem(
                            txn      = transaction,
                                onDelete = { viewModel.deleteTransaction(transaction) }
                            )
                        }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(txn: Transaction, onDelete: () -> Unit = {}) {
    val isExpense = txn.type.lowercase() == "expense"
    val isIncome  = txn.type.lowercase() == "income"
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction?") },
            text  = { Text("Delete ₹${String.format(java.util.Locale.getDefault(), "%,.0f", txn.amount)} ${txn.category}? This will reverse the account balance.") },
            confirmButton = {
                Button(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    val amountColor = when {
        isExpense -> Color(0xFFD32F2F)
        isIncome -> Color(0xFF388E3C)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(amountColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(txn.category),
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = txn.category,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = txn.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${if (isExpense) "-" else if (isIncome) "+" else ""}₹ ${String.format(Locale.getDefault(), "%,.0f", txn.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = amountColor
                    )
                )

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete",
                        modifier = Modifier.size(20.dp), tint = Color(0xFFEF4444).copy(alpha = 0.7f))
                }
            }

            if (txn.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = txn.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (txn.fromAcct.isNotEmpty()) {
                    Text("From: ${txn.fromAcct}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                if (txn.fromAcct.isNotEmpty() && txn.toAcct.isNotEmpty()) Spacer(Modifier.width(8.dp))
                if (txn.toAcct.isNotEmpty()) {
                    Text("To: ${txn.toAcct}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun EmptyTransactionState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No transactions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "food" -> Icons.Default.Restaurant
        "fuel" -> Icons.Default.LocalGasStation
        "shopping" -> Icons.Default.ShoppingCart
        "salary" -> Icons.Default.Payments
        "medical" -> Icons.Default.MedicalServices
        "bills" -> Icons.Default.Receipt
        "investment" -> Icons.Default.TrendingUp
        "lending", "loan repayment" -> Icons.Default.Handshake
        "debt", "debt repayment" -> Icons.Default.CreditCard
        else -> Icons.Default.AccountBalanceWallet
    }
}
