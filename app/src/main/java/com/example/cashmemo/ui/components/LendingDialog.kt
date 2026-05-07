package com.example.cashmemo.ui.components

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
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.data.model.Person
import com.example.cashmemo.logic.DateUtils
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
    var date by remember { mutableStateOf(initialBorrower?.date ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialBorrower?.due ?: "") }
    var notes by remember { mutableStateOf(initialBorrower?.notes ?: "") }
    var expandedType by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initialBorrower?.type ?: "Simple Interest") }
    
    var expandedSource by remember { mutableStateOf(false) }
    val sources = listOf("Cash", "Bank", "Investment", "Debts", "Other Receivables")
    var selectedSource by remember { mutableStateOf(sources[0]) }
    var sourceEntityName by remember { mutableStateOf("") }

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
                    ExposedDropdownMenuBox(
                        expanded = expandedSource,
                        onExpandedChange = { expandedSource = !expandedSource }
                    ) {
                        OutlinedTextField(
                            value = selectedSource,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Source of Money") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSource) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedSource, onDismissRequest = { expandedSource = false }) {
                            sources.forEach { src ->
                                DropdownMenuItem(text = { Text(src) }, onClick = { selectedSource = src; expandedSource = false })
                            }
                        }
                    }
                    
                    val labelText = when (selectedSource) {
                        "Bank" -> "Which Bank?"
                        "Investment" -> "Which Investment?"
                        "Debts" -> "Which Debt / Loan?"
                        "Other Receivables" -> "Who owed you this?"
                        else -> ""
                    }

                    if (labelText.isNotEmpty()) {
                        OutlinedTextField(
                            value = sourceEntityName,
                            onValueChange = { sourceEntityName = it },
                            label = { Text(labelText) },
                            modifier = Modifier.fillMaxWidth()
                        )
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
                            val finalSource = when (selectedSource) {
                                "Cash" -> "Cash in Hand"
                                else -> sourceEntityName.ifEmpty { selectedSource }
                            }
                            val rawId = initialBorrower?.id ?: ""
                            val borrower = Borrower(
                                id     = if (rawId.isBlank()) UUID.randomUUID().toString() else rawId,
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

