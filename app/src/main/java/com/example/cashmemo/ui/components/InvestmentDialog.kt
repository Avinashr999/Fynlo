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
import com.example.cashmemo.data.model.Investment
import com.example.cashmemo.logic.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvestmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Investment, String) -> Unit,
    initialInvestment: Investment? = null
) {
    var name by remember { mutableStateOf(initialInvestment?.name ?: "") }
    var amount by remember { mutableStateOf(initialInvestment?.invested?.toString() ?: "") }
    var currentVal by remember { mutableStateOf(initialInvestment?.currentVal?.toString() ?: "") }
    var date by remember { mutableStateOf(initialInvestment?.date ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var type by remember { mutableStateOf(initialInvestment?.type ?: "Stocks") }
    var notes by remember { mutableStateOf(initialInvestment?.notes ?: "") }
    
    var expandedSource by remember { mutableStateOf(false) }
    val sources = listOf("Cash", "Bank", "Debts", "Custom")
    var selectedSource by remember { mutableStateOf(sources[0]) }
    var sourceEntityName by remember { mutableStateOf("") }

    val investTypes = listOf("Stocks", "Mutual Funds", "Gold", "FD", "Business", "Real Estate", "Other")
    var expandedType by remember { mutableStateOf(false) }

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
                Text(
                    text = if (initialInvestment == null) "Add Investment" else "Edit Investment",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Asset Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExposedDropdownMenuBox(
                    expanded = expandedType,
                    onExpandedChange = { expandedType = !expandedType }
                ) {
                    OutlinedTextField(
                        value = type,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Asset Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) {
                        investTypes.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; expandedType = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Invested Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = currentVal,
                    onValueChange = { currentVal = it },
                    label = { Text("Current Market Value (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialInvestment == null) {
                    ExposedDropdownMenuBox(
                        expanded = expandedSource,
                        onExpandedChange = { expandedSource = !expandedSource }
                    ) {
                        OutlinedTextField(
                            value = selectedSource,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Funding Source") },
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
                        "Debts" -> "Which Debt / Loan?"
                        "Custom" -> "Custom Source Name"
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

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Purpose") },
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
                            val finalSource = when (selectedSource) {
                                "Cash" -> "Cash in Hand"
                                "Custom" -> sourceEntityName
                                else -> sourceEntityName.ifEmpty { selectedSource }
                            }
                            val investment = Investment(
                                id = initialInvestment?.id ?: UUID.randomUUID().toString(),
                                name = name,
                                type = type,
                                invested = amount.toDoubleOrNull() ?: 0.0,
                                currentVal = currentVal.toDoubleOrNull() ?: (amount.toDoubleOrNull() ?: 0.0),
                                date = DateUtils.parseInput(date),
                                notes = notes
                            )
                            onConfirm(investment, finalSource)
                        }
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

