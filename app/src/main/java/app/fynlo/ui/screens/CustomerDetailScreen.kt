package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.components.CollectPaymentDialog
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    borrowerId: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
        val haptic = LocalHapticFeedback.current
val borrowers by viewModel.borrowers.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = Locale.getDefault()

    val borrower = borrowers.find { it.id == borrowerId }

    if (borrower == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Customer not found")
        }
        return
    }

    val loanPayments = allPayments
        .filter { it.loanId == borrowerId }
        .sortedByDescending { it.date }

    val interest = InterestEngine.calcIntAccrued(
        borrower.amount, borrower.rate, borrower.date, borrower.type, borrower.due, borrower.paid
    )
    val totalOutstanding = InterestEngine.calcOutstanding(borrower.amount, interest, borrower.paid)

    val context = androidx.compose.ui.platform.LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var showCollectDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated, _ ->
                viewModel.updateBorrower(updated)
                showEditDialog = false
            },
            initialBorrower = borrower
        )
    }

    if (showCollectDialog) {
        CollectPaymentDialog(
            borrower = borrower,
            accounts = accounts,
            onDismiss = { showCollectDialog = false },
            onConfirm = { payment, dest ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.collectLoanPayment(payment, dest)
                showCollectDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Borrower?") },
            text = {
                Text(
                    "This will permanently delete ${borrower.name} and all their payment records. " +
                    "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteBorrower(borrower)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

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
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        val file = java.io.File(
                            context.cacheDir,
                            "loan_statement_${borrower.name.replace(" ", "_")}.pdf"
                        )
                        file.outputStream().use { os ->
                            app.fynlo.logic.ExportUtility.generateLoanStatementPDF(
                                os, borrower, emptyList(), interest, totalOutstanding,
                                currencyCode = currencyCode,
                            )
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share Loan Statement"
                        ))
                    }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
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
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Current Balance", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        CurrencyFormatter.hero(totalOutstanding, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalOutstanding > 0) MaterialTheme.colorScheme.error
                                    else Emerald500
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Principal", CurrencyFormatter.detail(borrower.amount, currencyCode, locale))
                        DetailItem("Interest",  CurrencyFormatter.detail(interest, currencyCode, locale))
                        DetailItem("Paid",      CurrencyFormatter.detail(borrower.paid, currencyCode, locale))
                    }
                    if (borrower.rate > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rate: ${borrower.rate}% • ${app.fynlo.logic.InterestEngine.label(borrower.type)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (totalOutstanding > 0) {
                item {
                    Button(
                        onClick = { showCollectDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Collect Payment")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Payment History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (loanPayments.isNotEmpty()) {
                        Text(
                            "${loanPayments.size} payment${if (loanPayments.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (loanPayments.isEmpty()) {
                item {
                    Text(
                        "No payments collected yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            } else {
                items(loanPayments, key = { it.id }) { payment ->
                    PaymentItem(payment, currencyCode, locale)
                }
            }

            if (borrower.notes.isNotBlank()) {
                item {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(borrower.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentItem(payment: Payment, currencyCode: String, locale: Locale) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Emerald500.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Payments,
                contentDescription = null,
                tint = Emerald500,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                payment.type.ifBlank { "Repayment" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                DateUtils.formatToDisplay(payment.date),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (payment.notes.isNotBlank()) {
                Text(
                    payment.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "+${CurrencyFormatter.detail(payment.amount, currencyCode, locale)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Emerald500
            )
        )
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}
