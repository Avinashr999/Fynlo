package com.example.cashmemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cashmemo.data.model.Borrower
import com.example.cashmemo.data.model.Debt
import com.example.cashmemo.data.model.DebtPayment
import com.example.cashmemo.data.model.Payment
import com.example.cashmemo.logic.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentDialog(
    borrower: Borrower,
    onDismiss: () -> Unit,
    onConfirm: (Payment, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    
    var expandedDest by remember { mutableStateOf(false) }
    val destinations = listOf("Cash", "Bank")
    var selectedDest by remember { mutableStateOf(destinations[0]) }
    var bankDetailName by remember { mutableStateOf("") }

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
                Text("From: ${borrower.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Received (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedDest,
                    onExpandedChange = { expandedDest = !expandedDest }
                ) {
                    OutlinedTextField(
                        value = selectedDest,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Where to deposit?") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDest) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedDest, onDismissRequest = { expandedDest = false }) {
                        destinations.forEach { dest ->
                            DropdownMenuItem(text = { Text(dest) }, onClick = { selectedDest = dest; expandedDest = false })
                        }
                    }
                }
                
                if (selectedDest == "Bank") {
                    OutlinedTextField(
                        value = bankDetailName,
                        onValueChange = { bankDetailName = it },
                        label = { Text("Which Bank?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
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
                                else -> bankDetailName.ifEmpty { selectedDest }
                            }
                            val payment = Payment(
                                id = UUID.randomUUID().toString(),
                                loanId = borrower.id,
                                name = borrower.name,
                                date = DateUtils.parseInput(date),
                                type = "Repayment",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                notes = notes
                            )
                            onConfirm(payment, finalDest)
                        }
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayDebtDialog(
    debt: Debt,
    onDismiss: () -> Unit,
    onConfirm: (DebtPayment, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    
    var expandedSrc by remember { mutableStateOf(false) }
    val sources = listOf("Cash", "Bank")
    var selectedSrc by remember { mutableStateOf(sources[0]) }
    var bankDetailName by remember { mutableStateOf("") }

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
                Text("To: ${debt.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Paid (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expandedSrc,
                    onExpandedChange = { expandedSrc = !expandedSrc }
                ) {
                    OutlinedTextField(
                        value = selectedSrc,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Source of Payment") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSrc) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedSrc, onDismissRequest = { expandedSrc = false }) {
                        sources.forEach { src ->
                            DropdownMenuItem(text = { Text(src) }, onClick = { selectedSrc = src; expandedSrc = false })
                        }
                    }
                }
                
                if (selectedSrc == "Bank") {
                    OutlinedTextField(
                        value = bankDetailName,
                        onValueChange = { bankDetailName = it },
                        label = { Text("Which Bank?") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Payment Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalSrc = when (selectedSrc) {
                                "Cash" -> "Cash in Hand"
                                else -> bankDetailName.ifEmpty { selectedSrc }
                            }
                            val payment = DebtPayment(
                                id = UUID.randomUUID().toString(),
                                debtId = debt.id,
                                name = debt.name,
                                date = DateUtils.parseInput(date),
                                type = "Debt Repayment",
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                notes = notes
                            )
                            onConfirm(payment, finalSrc)
                        }
                    ) {
                        Text("Confirm Payment")
                    }
                }
            }
        }
    }
}