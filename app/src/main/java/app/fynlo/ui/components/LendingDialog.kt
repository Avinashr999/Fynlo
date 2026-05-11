package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Person
import app.fynlo.logic.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLendingDialog(
    viewModel: FinanceViewModel, // Added to fetch people
    onDismiss: () -> Unit,
    onConfirm: (Borrower, String) -> Unit,
    initialBorrower: Borrower? = null
) {
    val people by viewModel.people.collectAsState()
    
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personExpanded by remember { mutableStateOf(false) }

    var amount by remember { mutableStateOf(initialBorrower?.amount?.toString() ?: "") }
    var rate by remember { mutableStateOf(initialBorrower?.rate?.toString() ?: "") }
    var date by remember { mutableStateOf(initialBorrower?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialBorrower?.due?.let { DateUtils.formatToDisplay(it) } ?: "") }
    var notes by remember { mutableStateOf(initialBorrower?.notes ?: "") }
    var expandedType by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initialBorrower?.type ?: "Simple Interest") }
    
    val accounts by viewModel.accounts.collectAsState()
    var expandedSource by remember { mutableStateOf(false) }
    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(app.fynlo.data.model.Account(id="cash", name="Cash in Hand", type="Cash", balance=0.0))
    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    val interestTypes = listOf("Simple Interest", "Reducing Balance", "Compound Interest", "Both")

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
                Text(if (initialBorrower == null) "Add Lending Record" else "Edit Lending Record", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // PERSON SELECTION
                ExposedDropdownMenuBox(
                    expanded = personExpanded,
                    onExpandedChange = { personExpanded = !personExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPerson?.name ?: initialBorrower?.name ?: "Select Person",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Borrower (Pick from Contacts)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                        people.forEach { person ->
                            DropdownMenuItem(
                                text = { Text("${person.name} (${person.id})") },
                                onClick = { selectedPerson = person; personExpanded = false }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialBorrower == null) {
                    // Account dropdown — actual accounts so balance is correctly updated
                    ExposedDropdownMenuBox(
                        expanded = expandedSource,
                        onExpandedChange = { expandedSource = !expandedSource }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Lend from Account") },
                            supportingText = {
                                Text("${selectedAccount.type}  •  Balance: ₹${String.format("%,.0f", selectedAccount.balance)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSource) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedSource, onDismissRequest = { expandedSource = false }) {
                            accountOptions.forEach { acct ->
                                DropdownMenuItem(
                                    text = {
                                        androidx.compose.foundation.layout.Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
                                        ) {
                                            androidx.compose.foundation.layout.Column {
                                                Text(acct.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(acct.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text("₹${String.format("%,.0f", acct.balance)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = app.fynlo.ui.theme.Emerald500)
                                        }
                                    },
                                    onClick = { selectedAccount = acct; expandedSource = false }
                                )
                            }
                        }
                    }
                }

                DatePickerField(value = date, onValueChange = { date = it }, label = "Lending Date")

                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType }
                ) {
                    OutlinedTextField(
                        value = selectedType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Interest Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        interestTypes.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { selectedType = type; expandedType = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Annual Interest Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(value = due, onValueChange = { due = it }, label = "Due Date", optional = true)
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalSource = if (initialBorrower != null) "Cash in Hand"
                                else selectedAccount.name
                            // Store source account on borrower for display on card
                            val rawId = initialBorrower?.id ?: ""
                            val borrower = Borrower(
                                id     = if (rawId.isBlank()) UUID.randomUUID().toString() else rawId,
                                sourceAccount = if (initialBorrower == null) selectedAccount.name else initialBorrower.sourceAccount,
                                name   = selectedPerson?.name ?: initialBorrower?.name ?: "Unknown",
                                phone  = selectedPerson?.phone ?: initialBorrower?.phone ?: "",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                rate   = rate.toDoubleOrNull() ?: 0.0,
                                date   = DateUtils.parseInput(date),
                                due    = if (due.isNotEmpty()) DateUtils.parseInput(due) else "",
                                type   = selectedType,
                                status = initialBorrower?.status ?: "Active",
                                notes  = notes,
                                paid   = initialBorrower?.paid ?: 0.0
                            )
                            onConfirm(borrower, finalSource)
                        },
                        enabled = (selectedPerson != null || initialBorrower != null) && amount.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

