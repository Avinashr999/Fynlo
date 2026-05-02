package com.example.cashmemo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.logic.DateUtils
import com.example.cashmemo.logic.InterestEngine
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    borrowerId: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val borrowers by viewModel.borrowers.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    
    val borrower = borrowers.find { it.id == borrowerId }
    
    if (borrower == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Customer not found")
        }
        return
    }

    // Filter transactions related to this customer (Repayments)
    val history = transactions.filter { it.desc.contains(borrower.name, ignoreCase = true) }

    val interest = InterestEngine.calcIntAccrued(
        borrower.amount, borrower.rate, borrower.date, borrower.type, borrower.due, borrower.paid
    )
    val totalOutstanding = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(borrower.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Generate and share loan statement PDF
                        val file = java.io.File(context.cacheDir, "loan_statement_${borrower.name.replace(" ","_")}.pdf")
                        file.outputStream().use { os ->
                            com.example.cashmemo.logic.ExportUtility.generateLoanStatementPDF(
                                os, borrower, history, interest, totalOutstanding
                            )
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.cashmemo.finance.provider", file)
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share Loan Statement"
                        ))
                    }) {
                        Icon(androidx.compose.material.icons.Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = {
                        viewModel.deleteBorrower(borrower)
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Current Balance", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "₹ ${String.format(Locale.getDefault(), "%,.0f", totalOutstanding)}",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            DetailItem("Principal", "₹${borrower.amount.toInt()}")
                            DetailItem("Interest", "₹${interest.toInt()}")
                            DetailItem("Paid", "₹${borrower.paid.toInt()}")
                        }
                    }
                }
            }

            item {
                Text("Payment History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }

            if (history.isEmpty()) {
                item {
                    Text("No payment records found.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            } else {
                items(history) { txn ->
                    RepaymentItem(txn)
                }
            }
        }
    }
}

@Composable
fun RepaymentItem(txn: com.example.cashmemo.data.model.Transaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFE8F5E9),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Payments, contentDescription = null, tint = Color(0xFF388E3C), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(txn.category, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(DateUtils.formatToDisplay(txn.date), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Text(
                "₹ ${String.format(Locale.getDefault(), "%,.0f", txn.amount)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF388E3C))
            )
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}