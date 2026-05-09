package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
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
import app.fynlo.logic.DateUtils
import java.util.*

// ─── Collect Loan Repayment ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentDialog(
    borrower: Borrower,
    accounts: List<Account>,          // real accounts from ViewModel
    onDismiss: () -> Unit,
    onConfirm: (Payment, String) -> Unit
) {
    var amount   by remember { mutableStateOf("") }
    var date     by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    // Build the full list: real accounts first, then "Cash in Hand" as fallback
    val accountOptions: List<Account> = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))

    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp).imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {

                Text("Collect Repayment", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "From: ${borrower.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(20.dp))

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Received (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Deposit destination — real accounts dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deposit into which account?") },
                        supportingText = {
                            Text(
                                "${selectedAccount.type}  •  Balance: ₹${String.format("%,.0f", selectedAccount.balance)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (acct.type.lowercase()) {
                                                    "cash"  -> Icons.Default.Wallet
                                                    "upi"   -> Icons.Default.MonetizationOn
                                                    else    -> Icons.Default.AccountBalance
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    acct.type,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            "₹${String.format("%,.0f", acct.balance)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Color(0xFF059669) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                onClick = { selectedAccount = acct; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Date
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val payment = Payment(
                                id      = UUID.randomUUID().toString(),
                                loanId  = borrower.id,
                                name    = borrower.name,
                                date    = DateUtils.parseInput(date),
                                type    = "Repayment",
                                amount  = amount.toDoubleOrNull() ?: 0.0,
                                notes   = notes
                            )
                            onConfirm(payment, selectedAccount.name)
                        },
                        enabled = amount.toDoubleOrNull() != null && amount.isNotBlank()
                    ) {
                        Text("Confirm")
                    }
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
    accounts: List<Account>,          // real accounts from ViewModel
    onDismiss: () -> Unit,
    onConfirm: (DebtPayment, String) -> Unit
) {
    var amount   by remember { mutableStateOf("") }
    var date     by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val accountOptions: List<Account> = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))

    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp).imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {

                Text("Pay Debt", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "To: ${debt.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Paid (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // Source account — real accounts dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccount.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pay from which account?") },
                        supportingText = {
                            Text(
                                "${selectedAccount.type}  •  Balance: ₹${String.format("%,.0f", selectedAccount.balance)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (acct.type.lowercase()) {
                                                    "cash"  -> Icons.Default.Wallet
                                                    "upi"   -> Icons.Default.MonetizationOn
                                                    else    -> Icons.Default.AccountBalance
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    acct.type,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Text(
                                            "₹${String.format("%,.0f", acct.balance)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Color(0xFF059669) else MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                onClick = { selectedAccount = acct; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val payment = DebtPayment(
                                id     = UUID.randomUUID().toString(),
                                debtId = debt.id,
                                name   = debt.name,
                                date   = DateUtils.parseInput(date),
                                type   = "Debt Repayment",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                notes  = notes
                            )
                            onConfirm(payment, selectedAccount.name)
                        },
                        enabled = amount.toDoubleOrNull() != null && amount.isNotBlank()
                    ) {
                        Text("Confirm Payment")
                    }
                }
            }
        }
    }
}
