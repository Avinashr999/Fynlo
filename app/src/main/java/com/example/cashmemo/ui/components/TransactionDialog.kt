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
import com.example.cashmemo.data.model.Transaction
import com.example.cashmemo.logic.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    
    var expandedCategory by remember { mutableStateOf(false) }
    val categories = listOf("Food", "Rent", "Fuel", "Shopping", "Salary", "Investment", "Lending", "Custom")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    var expandedSrc by remember { mutableStateOf(false) }
    val sources = listOf("Cash", "Bank", "Investment", "Debts", "Custom")
    var selectedSrc by remember { mutableStateOf(sources[0]) }
    var sourceDetailName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Add Manual Entry", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // SOURCE SELECTION
                ExposedDropdownMenuBox(
                    expanded = expandedSrc,
                    onExpandedChange = { expandedSrc = !expandedSrc }
                ) {
                    OutlinedTextField(
                        value = selectedSrc,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Account / Source") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSrc) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedSrc, onDismissRequest = { expandedSrc = false }) {
                        sources.forEach { src ->
                            DropdownMenuItem(text = { Text(src) }, onClick = { selectedSrc = src; expandedSrc = false })
                        }
                    }
                }
                
                val sourceLabel = when (selectedSrc) {
                    "Bank" -> "Which Bank?"
                    "Investment" -> "Which Investment?"
                    "Debts" -> "Which Debt / Loan?"
                    "Custom" -> "Custom Source Name"
                    else -> ""
                }

                if (sourceLabel.isNotEmpty()) {
                    OutlinedTextField(
                        value = sourceDetailName,
                        onValueChange = { sourceDetailName = it },
                        label = { Text(sourceLabel) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { selectedCategory = cat; expandedCategory = false })
                        }
                    }
                }
                
                if (selectedCategory == "Custom") {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        label = { Text("Custom Category Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalAccount = when (selectedSrc) {
                                "Cash" -> "Cash in Hand"
                                "Custom" -> sourceDetailName
                                else -> sourceDetailName.ifEmpty { selectedSrc }
                            }
                            val isIncome = selectedCategory == "Salary"
                            
                            val txn = Transaction(
                                id = UUID.randomUUID().toString(),
                                date = DateUtils.parseInput(date),
                                type = if (isIncome) "Income" else "Expense",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                category = if (selectedCategory == "Custom") customCategory else selectedCategory,
                                desc = desc,
                                notes = notes,
                                fromAcct = if (isIncome) "" else finalAccount,
                                toAcct = if (isIncome) finalAccount else ""
                            )
                            onConfirm(txn)
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}