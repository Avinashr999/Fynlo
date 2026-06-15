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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Account
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Person
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.Emerald500
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Debt, String) -> Unit,
    initialDebt: Debt? = null,
    currencyCode: String = "INR",
) {
    val people   by viewModel.people.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val locale = LocalLocale.current.platformLocale

    var selectedPerson  by remember { mutableStateOf<Person?>(null) }
    var personExpanded  by remember { mutableStateOf(false) }
    var customLenderName by remember { mutableStateOf(initialDebt?.name ?: "") }
    var useCustomName   by remember { mutableStateOf(false) }

    var amount   by remember { mutableStateOf(initialDebt?.amount?.toString() ?: "") }
    var rate     by remember { mutableStateOf(initialDebt?.rate?.toString() ?: "") }
    var tenure   by remember { mutableStateOf(initialDebt?.tenure?.let { if (it > 0) it.toString() else "" } ?: "") }
    var date     by remember { mutableStateOf(initialDebt?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due      by remember { mutableStateOf(initialDebt?.due?.let { DateUtils.formatToDisplay(it) } ?: "") }
    var notes    by remember { mutableStateOf(initialDebt?.notes ?: "") }

    // Actual account dropdown
    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))
    var selectedAccount  by remember { mutableStateOf(accountOptions.first()) }
    var expandedDest     by remember { mutableStateOf(false) }

    var expandedIntType  by remember { mutableStateOf(false) }
    val interestTypes    = listOf("Simple Interest", "Reducing Balance", "Compound Interest", "Both")
    var selectedIntType  by remember { mutableStateOf(initialDebt?.intType ?: "Simple Interest") }
    var submitting       by remember(initialDebt?.id) { mutableStateOf(false) }

    val lenderName = if (useCustomName) customLenderName else selectedPerson?.name ?: ""
    val isValid    = lenderName.isNotBlank() && amount.isNotEmpty()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 24.dp).imePadding(),
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(if (initialDebt == null) "Add New Debt" else "Edit Debt",
                    style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // ── Lender selection ─────────────────────────────────────
                if (!useCustomName) {
                    ExposedDropdownMenuBox(expanded = personExpanded, onExpandedChange = { personExpanded = !personExpanded }) {
                        OutlinedTextField(
                            value = selectedPerson?.name ?: "Select from Contacts",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lender") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                            people.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = { selectedPerson = person; personExpanded = false }
                                )
                            }
                        }
                    }
                    TextButton(onClick = { useCustomName = true }) {
                        Text("Type lender name manually instead", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    OutlinedTextField(
                        value = customLenderName,
                        onValueChange = { customLenderName = it },
                        label = { Text("Lender Name") },
                        placeholder = { Text("e.g. SBI Bank, Relative name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { useCustomName = false; customLenderName = "" }) {
                        Text("Pick from Contacts instead", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Deposit to account (actual accounts) ──────────────────
                if (initialDebt == null) {
                    ExposedDropdownMenuBox(expanded = expandedDest, onExpandedChange = { expandedDest = !expandedDest }) {
                        OutlinedTextField(
                            value = selectedAccount.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Received into Account") },
                            supportingText = {
                                Text("${selectedAccount.type}  •  Balance: ${CurrencyFormatter.detail(selectedAccount.balance, currencyCode, locale)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDest) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedDest, onDismissRequest = { expandedDest = false }) {
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
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = Emerald500)
                                        }
                                    },
                                    onClick = { selectedAccount = acct; expandedDest = false }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ── Amount + dates ────────────────────────────────────────
                OutlinedTextField(value = amount, onValueChange = { amount = it },
                    label = { Text("Amount (${CurrencyUtils.symbolFor(currencyCode)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                DatePickerField(value = date, onValueChange = { date = it }, label = "Date Taken")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rate, onValueChange = { rate = it },
                        label = { Text("Rate (%)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))

                    OutlinedTextField(value = tenure, onValueChange = { tenure = it },
                        label = { Text("Tenure (mo)") },
                        placeholder = { Text("Optional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f))
                }

                // ── Interest type ─────────────────────────────────────────
                // C12 (3.2.25) — display labels routed through
                // `InterestEngine.label(...)` so "Both" renders as "SI + CI"
                // per audit fix #9. Stored value (`selectedIntType`) stays
                // raw — DB schema and InterestEngine branch on the raw form.
                ExposedDropdownMenuBox(expanded = expandedIntType, onExpandedChange = { expandedIntType = !expandedIntType }) {
                    OutlinedTextField(value = app.fynlo.logic.InterestEngine.label(selectedIntType), onValueChange = {}, readOnly = true,
                        label = { Text("Interest Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntType) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth())
                    ExposedDropdownMenu(expanded = expandedIntType, onDismissRequest = { expandedIntType = false }) {
                        interestTypes.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(app.fynlo.logic.InterestEngine.label(t)) },
                                onClick = { selectedIntType = t; expandedIntType = false },
                            )
                        }
                    }
                }

                DatePickerField(value = due, onValueChange = { due = it }, label = "Due Date", optional = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes / Purpose") }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (submitting) return@Button
                            submitting = true
                            val debt = Debt(
                                id      = initialDebt?.id?.takeIf { it.isNotBlank() } ?: app.fynlo.logic.Ids.newId(),
                                name    = lenderName,
                                amount  = amount.toDoubleOrNull() ?: 0.0,
                                rate    = rate.toDoubleOrNull() ?: 0.0,
                                date    = DateUtils.parseInput(date),
                                due     = if (due.isNotEmpty()) DateUtils.parseInput(due) else "",
                                tenure  = tenure.toIntOrNull() ?: 0,
                                notes   = notes,
                                status  = initialDebt?.status ?: "Active",
                                type    = initialDebt?.type ?: "Friend / Family",
                                intType = selectedIntType,
                                paid    = initialDebt?.paid ?: 0.0
                            )
                            onConfirm(debt, selectedAccount.name)
                        },
                        enabled = isValid && !submitting
                    ) { Text("Save") }
                }
                // C17 (3.2.42) — disabled-button hint.
                val debtDisabledReason: String? = when {
                    lenderName.isBlank() -> "Enter the lender's name to continue"
                    amount.isEmpty()     -> "Enter the borrowed amount to continue"
                    else                 -> null
                }
                DisabledButtonHint(debtDisabledReason)
            }
        }
    }
}
