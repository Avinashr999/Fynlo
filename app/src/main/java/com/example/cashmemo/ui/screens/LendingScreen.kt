package com.example.cashmemo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.logic.DateUtils
import com.example.cashmemo.logic.InterestEngine
import com.example.cashmemo.ui.components.AddLendingDialog
import com.example.cashmemo.ui.components.CollectPaymentDialog
import java.util.Locale

@Composable
fun LendingScreen(viewModel: FinanceViewModel, onNavigateToDetail: (String) -> Unit = {}) {
    val borrowers by viewModel.borrowers.collectAsState()
    var editingBorrower by remember { mutableStateOf<Borrower?>(null) }
    var collectingForBorrower by remember { mutableStateOf<Borrower?>(null) }

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
                Text(
                    "Lending Management", 
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            if (borrowers.isEmpty()) {
                item {
                    EmptyLendingState()
                }
            } else {
                items(borrowers) { borrower ->
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
        totalPaid = borrower.paid // PASS PAYMENTS
    )
    val outs = InterestEngine.calcOutstanding(
        borrower.amount, interest, borrower.paid
    )
    
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
fun EmptyLendingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Person, 
            contentDescription = null, 
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No active loans", 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Add people you've lent money to.", 
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}