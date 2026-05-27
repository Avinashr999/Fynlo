package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.DebtPayment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddDebtDialog
import app.fynlo.ui.components.PayDebtDialog
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed
import java.util.Locale

/**
 * Owed-side counterpart to [CustomerDetailScreen]. Same visual structure
 * (hero outstanding → action button → payment history → notes) so the
 * Lent and Owed detail surfaces feel like one design (UX_AUDIT §C12 fix #5).
 *
 * Hosts all per-debt actions per audit #6/#7: Pay (primary button), Edit
 * + Delete (TopBar). The card in [DebtScreen] is now action-free — every
 * action lives here as a proper labelled button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtDetailScreen(
    debtId: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val haptic        = LocalHapticFeedback.current
    val debts         by viewModel.debts.collectAsState()
    val allPayments   by viewModel.debtPayments.collectAsState()
    val accounts      by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode  = currentProject?.currency ?: "INR"
    val locale        = Locale.getDefault()

    val debt = debts.find { it.id == debtId }
    if (debt == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Debt not found")
        }
        return
    }

    val debtPayments = allPayments
        .filter { it.debtId == debtId }
        .sortedByDescending { it.date }

    val interest = InterestEngine.calcIntAccrued(
        debt.amount, debt.rate, debt.date, debt.intType, debt.due, debt.paid
    )
    val totalOutstanding = InterestEngine.calcOutstanding(debt.amount, interest, debt.paid)

    var showEditDialog    by remember { mutableStateOf(false) }
    var showPayDialog     by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showEditDialog) {
        AddDebtDialog(
            viewModel    = viewModel,
            onDismiss    = { showEditDialog = false },
            onConfirm    = { updated, _ -> viewModel.updateDebt(updated); showEditDialog = false },
            initialDebt  = debt
        )
    }
    if (showPayDialog) {
        PayDebtDialog(
            debt      = debt,
            accounts  = accounts,
            onDismiss = { showPayDialog = false },
            onConfirm = { payment, source ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.payDebt(payment, source)
                showPayDialog = false
            }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete debt?") },
            text  = { Text("This will permanently delete \"${debt.name}\" and reverse the linked account entries. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteDebt(debt)
                        showDeleteConfirm = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(debt.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
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
                    Text(
                        "Current Outstanding",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        DetailItem("Principal", CurrencyFormatter.detail(debt.amount, currencyCode, locale))
                        DetailItem("Interest",  CurrencyFormatter.detail(interest, currencyCode, locale))
                        DetailItem("Paid",      CurrencyFormatter.detail(debt.paid, currencyCode, locale))
                    }
                    if (debt.rate > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Rate: ${debt.rate}% • ${app.fynlo.logic.InterestEngine.label(debt.intType)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (debt.due.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Due ${DateUtils.formatToDisplay(debt.due)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (totalOutstanding > 0) {
                item {
                    Button(
                        onClick = { showPayDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SemanticRed)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Make Payment")
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
                    if (debtPayments.isNotEmpty()) {
                        Text(
                            app.fynlo.logic.pluralize(debtPayments.size, "payment"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (debtPayments.isEmpty()) {
                item {
                    Text(
                        "No payments made yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            } else {
                items(debtPayments, key = { it.id }) { payment ->
                    DebtPaymentItem(payment, currencyCode, locale)
                }
            }

            if (debt.notes.isNotBlank()) {
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
                        Text(debt.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtPaymentItem(payment: DebtPayment, currencyCode: String, locale: Locale) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(SemanticRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CreditCard,
                contentDescription = null,
                tint = SemanticRed,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                payment.type.ifBlank { "Payment" },
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
            "-${CurrencyFormatter.detail(payment.amount, currencyCode, locale)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = SemanticRed
            )
        )
    }
}
