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
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Person
import app.fynlo.logic.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDebtDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Debt, String) -> Unit,
    initialDebt: Debt? = null
) {
    val people by viewModel.people.collectAsState()
    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var personExpanded by remember { mutableStateOf(false) }

    var customName by remember { mutableStateOf(initialDebt?.name ?: "") }
    var amount by remember { mutableStateOf(initialDebt?.amount?.toString() ?: "") }
    var rate by remember { mutableStateOf(initialDebt?.rate?.toString() ?: "") }
    var date by remember { mutableStateOf(initialDebt?.date ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialDebt?.due ?: "") }
    var notes by remember { mutableStateOf(initialDebt?.notes ?: "") }
    
    var expandedDest by remember { mutableStateOf(false) }
    val destinations = listOf("Cash", "Bank", "Investment", "Custom")
    var selectedDest by remember { mutableStateOf(destinations[0]) }
    var bankDetailName by remember { mutableStateOf("") }

    var expandedIntType by remember { mutableStateOf(false) }
    val interestTypes = listOf("Simple Interest", "Reducing Balance", "Compound Interest", "Both")
    var selectedIntType by remember { mutableStateOf(initialDebt?.intType ?: "Simple Interest") }

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
                Text(if (initialDebt == null) "Add New Debt" else "Edit Debt", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // PERSON SELECTION
                ExposedDropdownMenuBox(
                    expanded = personExpanded,
                    onExpandedChange = { personExpanded = !personExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedPerson?.name ?: initialDebt?.name ?: "Select Lender",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lender (Pick from Contacts)") },
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

                if (initialDebt == null) {
                    ExposedDropdownMenuBox(
                        expanded = expandedDest,
                        onExpandedChange = { expandedDest = !expandedDest }
                    ) {
                        OutlinedTextField(
                            value = selectedDest,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Where did you receive this money?") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDest) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedDest, onDismissRequest = { expandedDest = false }) {
                            destinations.forEach { dest ->
                                DropdownMenuItem(text = { Text(dest) }, onClick = { selectedDest = dest; expandedDest = false })
                            }
                        }
                    }
                    
                    val destLabel = when (selectedDest) {
                        "Bank" -> "Which Bank?"
                        "Investment" -> "Which Investment Account?"
                        "Custom" -> "Custom Account Name"
                        else -> ""
                    }

                    if (destLabel.isNotEmpty()) {
                        OutlinedTextField(
                            value = bankDetailName,
                            onValueChange = { bankDetailName = it },
                            label = { Text(destLabel) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(value = date, onValueChange = { date = it }, label = "Date Taken")
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text("Annual Interest Rate (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Interest Type dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedIntType,
                    onExpandedChange = { expandedIntType = !expandedIntType }
                ) {
                    OutlinedTextField(
                        value = selectedIntType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Interest Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntType) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedIntType, onDismissRequest = { expandedIntType = false }) {
                        interestTypes.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { selectedIntType = t; expandedIntType = false })
                        }
                    }
                }

                DatePickerField(value = due, onValueChange = { due = it }, label = "Due Date", optional = true)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalDest = when (selectedDest) {
                                "Cash" -> "Cash in Hand"
                                "Custom" -> bankDetailName
                                else -> bankDetailName.ifEmpty { selectedDest }
                            }
                            val rawId = initialDebt?.id ?: ""
                            val debt = Debt(
                                id = if (rawId.isBlank()) UUID.randomUUID().toString() else rawId,
                                name = selectedPerson?.name ?: initialDebt?.name ?: "Unknown",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                rate = rate.toDoubleOrNull() ?: 0.0,
                                date = DateUtils.parseInput(date),
                                due = if (due.isNotEmpty()) DateUtils.parseInput(due) else "",
                                notes = notes,
                                status = initialDebt?.status ?: "Active",
                                type = initialDebt?.type ?: "Friend / Family",
                                intType = selectedIntType,
                                paid = initialDebt?.paid ?: 0.0
                            )
                            onConfirm(debt, finalDest)
                        },
                        enabled = (selectedPerson != null || initialDebt != null) && amount.isNotEmpty()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

