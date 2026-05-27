package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import java.util.*
import app.fynlo.ui.theme.*

// ─── Collect Loan Repayment ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentDialog(
    borrower: Borrower,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Payment, String) -> Unit,
    currencyCode: String = "INR",
) {
    val today = java.time.LocalDate.now()
    val locale = Locale.getDefault()

    // Calculate accrued interest and outstanding using new split fields
    val accruedInterest = remember(borrower) {
        if (borrower.status == "Defaulted" && borrower.frozenInterest > 0) borrower.frozenInterest
        else InterestEngine.calcIntAccrued(
            borrower.amount, borrower.rate, borrower.date,
            borrower.type, borrower.due, totalPaid = borrower.paidPrincipal
        )
    }
    val interestOutstanding = remember(accruedInterest, borrower) {
        (accruedInterest - borrower.paidInterest).coerceAtLeast(0.0)
    }
    val principalOutstanding = remember(borrower) {
        (borrower.amount - borrower.paidPrincipal).coerceAtLeast(0.0)
    }
    val totalOutstanding = interestOutstanding + principalOutstanding

    // Payment fields
    var principalStr by remember { mutableStateOf("") }
    var interestStr  by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))
    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    val principalVal = principalStr.toDoubleOrNull() ?: 0.0
    val interestVal  = interestStr.toDoubleOrNull()  ?: 0.0
    val totalAmount  = principalVal + interestVal
    val isValid      = totalAmount > 0.0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 16.dp).imePadding(),
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {

                Text("Collect Repayment",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("From: ${borrower.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                // ── Outstanding summary ──────────────────────────────────────
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Outstanding", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Principal", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.detail(principalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SemanticRed)
                        }
                        if (borrower.rate > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Interest (${borrower.rate}% ${InterestEngine.label(borrower.type)})",
                                    style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestOutstanding, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SemanticAmber)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total Due", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(CurrencyFormatter.detail(totalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // ── Auto-suggest buttons ─────────────────────────────────────
                // Interest Only: show when interest is outstanding
                if (borrower.rate > 0 && interestOutstanding > 0) {
                    FilledTonalButton(
                        onClick = {
                            interestStr  = String.format("%.0f", interestOutstanding)
                            principalStr = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Interest Only — ${CurrencyFormatter.detail(interestOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Full Settlement: show whenever ANY amount is outstanding
                if (totalOutstanding > 0) {
                    FilledTonalButton(
                        onClick = {
                            interestStr  = String.format("%.0f", interestOutstanding)
                            principalStr = String.format("%.0f", principalOutstanding)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Full Settlement — ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Split entry fields ───────────────────────────────────────
                Text("Payment Breakdown", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = principalStr,
                        onValueChange = { principalStr = it },
                        label = { Text("Principal") },
                        placeholder = { Text("0") },
                        prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SemanticRed,
                            focusedLabelColor  = SemanticRed
                        )
                    )
                    OutlinedTextField(
                        value = interestStr,
                        onValueChange = { interestStr = it },
                        label = { Text("Interest") },
                        placeholder = { Text("0") },
                        prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SemanticAmber,
                            focusedLabelColor  = SemanticAmber
                        )
                    )
                }

                // ── Total ────────────────────────────────────────────────────
                if (totalAmount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Text("Total collecting: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.detail(totalAmount, currencyCode, locale),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Emerald500)
                    }
                }
                Spacer(Modifier.height(12.dp))

                // ── Destination account ──────────────────────────────────────
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedAccount.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deposit into account") },
                        supportingText = {
                            Text("${selectedAccount.type}  •  Balance: ${CurrencyFormatter.detail(selectedAccount.balance, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accountOptions.forEach { acct ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(when (acct.type.lowercase()) {
                                                "cash" -> Icons.Default.Wallet
                                                "upi"  -> Icons.Default.MonetizationOn
                                                else   -> Icons.Default.AccountBalance
                                            }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(acct.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(CurrencyFormatter.detail(acct.balance, currencyCode, locale),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = { selectedAccount = acct; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val payment = Payment(
                                id        = UUID.randomUUID().toString(),
                                loanId    = borrower.id,
                                name      = borrower.name,
                                date      = DateUtils.parseInput(date),
                                type      = when {
                                    principalVal > 0 && interestVal > 0 -> "Both"
                                    principalVal > 0 -> "Principal Only"
                                    else             -> "Interest Only"
                                },
                                amount    = totalAmount,
                                principal = principalVal,
                                interest  = interestVal,
                                notes     = notes
                            )
                            onConfirm(payment, selectedAccount.name)
                        },
                        enabled = isValid,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500)
                    ) { Text("Confirm ${CurrencyFormatter.detail(totalAmount, currencyCode, locale)}") }
                }
            }
        }
    }
}

// ─── Pay Debt ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayDebtDialog(
    debt: Debt,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (DebtPayment, String) -> Unit,
    currencyCode: String = "INR",
) {
    val locale = Locale.getDefault()
    val accruedInterest = remember(debt) {
        InterestEngine.calcIntAccrued(
            debt.amount, debt.rate, debt.date, debt.intType, debt.due,
            totalPaid = debt.paidPrincipal
        )
    }
    val interestOutstanding  = (accruedInterest - debt.paidInterest).coerceAtLeast(0.0)
    val principalOutstanding = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0)
    val totalOutstanding     = interestOutstanding + principalOutstanding

    var principalStr by remember { mutableStateOf("") }
    var interestStr  by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))
    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    val principalVal = principalStr.toDoubleOrNull() ?: 0.0
    val interestVal  = interestStr.toDoubleOrNull()  ?: 0.0
    val totalAmount  = principalVal + interestVal
    val isValid      = totalAmount > 0.0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 16.dp).imePadding(),
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Pay Debt",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("To: ${debt.name}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))

                // Outstanding summary
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("You Owe", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Principal", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.detail(principalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        if (debt.rate > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Interest (${debt.rate}%)", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestOutstanding, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total Due", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(CurrencyFormatter.detail(totalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Auto-suggest buttons
                if (debt.rate > 0 && interestOutstanding > 0) {
                    FilledTonalButton(onClick = {
                        interestStr = String.format("%.0f", interestOutstanding); principalStr = ""
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Interest Only — ${CurrencyFormatter.detail(interestOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Full Settlement — always show when any amount is outstanding
                if (totalOutstanding > 0) {
                    FilledTonalButton(onClick = {
                        interestStr  = String.format("%.0f", interestOutstanding)
                        principalStr = String.format("%.0f", principalOutstanding)
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Emerald500.copy(alpha = 0.15f))) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Emerald500)
                        Spacer(Modifier.width(6.dp))
                        Text("Full Settlement — ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}", color = Emerald500)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text("Payment Breakdown", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = principalStr, onValueChange = { principalStr = it },
                        label = { Text("Principal") }, placeholder = { Text("0") }, prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(value = interestStr, onValueChange = { interestStr = it },
                        label = { Text("Interest") }, placeholder = { Text("0") }, prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.error,
                            focusedLabelColor  = MaterialTheme.colorScheme.error))
                }

                if (totalAmount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Text("Total paying: ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.detail(totalAmount, currencyCode, locale),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = selectedAccount.name, onValueChange = {}, readOnly = true,
                        label = { Text("Pay from account") },
                        supportingText = {
                            Text("${selectedAccount.type}  •  Balance: ${CurrencyFormatter.detail(selectedAccount.balance, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accountOptions.forEach { acct ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(when (acct.type.lowercase()) {
                                                "cash" -> Icons.Default.Wallet
                                                "upi"  -> Icons.Default.MonetizationOn
                                                else   -> Icons.Default.AccountBalance
                                            }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(acct.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(CurrencyFormatter.detail(acct.balance, currencyCode, locale),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = { selectedAccount = acct; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(value = date, onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val payment = DebtPayment(
                                id        = UUID.randomUUID().toString(),
                                debtId    = debt.id,
                                name      = debt.name,
                                date      = DateUtils.parseInput(date),
                                type      = when {
                                    principalVal > 0 && interestVal > 0 -> "Both"
                                    principalVal > 0 -> "Principal Only"
                                    else             -> "Interest Only"
                                },
                                amount    = totalAmount,
                                principal = principalVal,
                                interest  = interestVal,
                                notes     = notes
                            )
                            onConfirm(payment, selectedAccount.name)
                        },
                        enabled = isValid,
                        shape   = RoundedCornerShape(14.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Pay ${CurrencyFormatter.detail(totalAmount, currencyCode, locale)}") }
                }
            }
        }
    }
}
