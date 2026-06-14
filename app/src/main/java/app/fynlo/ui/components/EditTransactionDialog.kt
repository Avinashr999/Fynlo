package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Transaction
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed

/**
 * EditTransactionDialog — extended in 3.2.81 (C13 #9) to support changing
 * **Type** (Income / Expense / Transfer) and **Account**. Pre-3.2.81 the
 * dialog could only edit amount / date / category / description / notes;
 * a user who picked the wrong type at creation had to delete + re-create
 * the transaction.
 *
 * C13 #7 (3.2.81): added Tags field. The `Transaction.tags` column has
 * existed since the entity was first defined; just no UI to read/write
 * it. Free-text, comma-separated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit,
    bankAccounts: List<String> = emptyList(),
) {
    var amount   by remember { mutableStateOf(transaction.amount.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var desc     by remember { mutableStateOf(transaction.desc) }
    var notes    by remember { mutableStateOf(transaction.notes) }
    var date     by remember { mutableStateOf(DateUtils.formatToDisplay(transaction.date)) }
    var category by remember { mutableStateOf(transaction.category) }
    // C13 #9 (3.2.81) — Type editable. Capitalise first letter to match
    // the canonical "Income" / "Expense" / "Transfer" form the rest of
    // the app uses (TransactionValidator + balance math compare on this).
    var type     by remember {
        mutableStateOf(transaction.type.replaceFirstChar { it.uppercase() }.takeIf { it in listOf("Income", "Expense", "Transfer") } ?: "Expense")
    }
    // C13 #9 — Account editable. For Income: toAcct is the destination.
    // For Expense: fromAcct is the source. For Transfer: both sides need
    // editing — we surface a single "Account" picker that maps to the
    // appropriate side via the type at save time. (Transfer rows that
    // need BOTH sides edited from this dialog are an edge case; users
    // who hit it can delete + re-create.)
    var account  by remember {
        mutableStateOf(transaction.fromAcct.ifBlank { transaction.toAcct })
    }
    // C13 #7 — Tags.
    var tags     by remember { mutableStateOf(transaction.tags) }

    // Category list pivots on the current (editable) type.
    val categories = remember(type) {
        app.fynlo.data.Categories.forType(type) + "Custom"
    }

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
                Text("Edit Transaction", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // C13 #9 — Type segmented row. Changing type re-pivots the
                // category list (e.g. switching Income → Expense hides
                // "Salary" / shows "Food"). The category state is left in
                // place; user re-picks if the old value doesn't apply.
                val typeOptions = listOf("Income", "Expense", "Transfer")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    typeOptions.forEachIndexed { idx, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(idx, typeOptions.size),
                            icon = {},
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = (if (t == "Income") Emerald500 else if (t == "Expense") SemanticRed else MaterialTheme.colorScheme.primary).copy(alpha = 0.14f),
                                activeContentColor = if (t == "Income") Emerald500 else if (t == "Expense") SemanticRed else MaterialTheme.colorScheme.primary,
                            ),
                            label = { Text(t, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(8.dp))
                DatePickerField(value = date, onValueChange = { date = it }, label = "Date")

                Spacer(Modifier.height(8.dp))
                var expandedCat by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expandedCat, onExpandedChange = { expandedCat = !expandedCat }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { category = c; expandedCat = false })
                        }
                    }
                }

                // C13 #9 — Account picker. Uses existing accounts to
                // prevent the orphan-account pattern from 3.2.59 — if the
                // user changes account, the new value must match a real
                // accounts.name or balance update silently no-ops.
                if (bankAccounts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    var expandedAcct by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expandedAcct, onExpandedChange = { expandedAcct = !expandedAcct }) {
                        OutlinedTextField(
                            value = account, onValueChange = {}, readOnly = true,
                            label = { Text(if (type == "Income") "Deposit to" else "Pay from") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedAcct) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        ExposedDropdownMenu(expanded = expandedAcct, onDismissRequest = { expandedAcct = false }) {
                            bankAccounts.forEach { a ->
                                DropdownMenuItem(text = { Text(a) }, onClick = { account = a; expandedAcct = false })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                // C13 #7 — Tags field.
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val parsed = amount.toDoubleOrNull() ?: transaction.amount
                        // C13 #9 — derive from/toAcct from the (possibly
                        // changed) type. For Transfer we preserve whichever
                        // sides the original had; Account picker only edits
                        // one side. Users with Transfer edits that need both
                        // sides can delete + re-create.
                        val newFromAcct = when (type) {
                            "Income"   -> ""
                            "Expense"  -> account
                            "Transfer" -> account.ifBlank { transaction.fromAcct }
                            else       -> transaction.fromAcct
                        }
                        val newToAcct = when (type) {
                            "Income"   -> account
                            "Expense"  -> ""
                            "Transfer" -> transaction.toAcct.ifBlank { account }
                            else       -> transaction.toAcct
                        }
                        onConfirm(transaction.copy(
                            amount    = parsed,
                            date      = DateUtils.parseInput(date),
                            type      = type,
                            fromAcct  = newFromAcct,
                            toAcct    = newToAcct,
                            desc      = desc,
                            notes     = notes,
                            tags      = tags.trim(),
                            category  = category,
                            updatedAt = System.currentTimeMillis()
                        ))
                    }) { Text("Save Changes") }
                }
            }
        }
    }
}
