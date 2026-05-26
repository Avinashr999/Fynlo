package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Transaction
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit,
    initialIsIncome: Boolean = false
) {
    var isIncome by remember { mutableStateOf(initialIsIncome) }
    var amount by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }

    // C05: the visible category list is driven by the Income/Expense toggle.
    // Recomputed via `remember(isIncome)` so the chip row updates the moment
    // the user flips the toggle, and the previously-selected value is cleared
    // (see the LaunchedEffect below) so e.g. "Food" can't be carried into an
    // Income transaction. "Custom" stays appended as the trailing affordance
    // for user-supplied categories.
    val categories = remember(isIncome) {
        (if (isIncome) app.fynlo.data.Categories.INCOME
         else          app.fynlo.data.Categories.EXPENSE) + "Custom"
    }
    var selectedCategory by remember { mutableStateOf("") }
    LaunchedEffect(isIncome) { selectedCategory = "" }

    val sources = listOf("Cash", "Bank", "Investment", "Debts", "Custom")
    var selectedSrc by remember { mutableStateOf(sources[0]) }
    var sourceDetailName by remember { mutableStateOf("") }

    val accent = if (isIncome) Emerald500 else SemanticRed

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 20.dp).imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Add Transaction",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                Spacer(Modifier.height(16.dp))

                // ── Expense / Income toggle ───────────────────────────────────
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isIncome,
                        onClick = { isIncome = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = SemanticRed.copy(alpha = 0.14f),
                            activeContentColor = SemanticRed
                        )
                    ) { Text("Expense") }
                    SegmentedButton(
                        selected = isIncome,
                        // C05: the special-case Food→Salary swap that used to live here
                        // is gone — the LaunchedEffect(isIncome) above resets selectedCategory
                        // to "" on every toggle flip, so no cross-type bleed is possible.
                        onClick = { isIncome = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = Emerald500.copy(alpha = 0.14f),
                            activeContentColor = Emerald500
                        )
                    ) { Text("Income") }
                }

                Spacer(Modifier.height(24.dp))

                // ── Big amount input (hero) ───────────────────────────────────
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    BasicAmountField(amount, accent) { amount = it.filter { c -> c.isDigit() || c == '.' } }
                }

                Spacer(Modifier.height(24.dp))

                // ── Category chips ────────────────────────────────────────────
                Text("Category", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                                selectedLabelColor = Emerald500
                            )
                        )
                    }
                }
                if (selectedCategory == "Custom") {
                    Spacer(Modifier.height(10.dp))
                    SoftField(customCategory, "Custom category name") { customCategory = it }
                }

                Spacer(Modifier.height(20.dp))

                // ── Account chips ─────────────────────────────────────────────
                Text(if (isIncome) "Deposit to" else "Pay from",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sources.forEach { src ->
                        FilterChip(
                            selected = selectedSrc == src,
                            onClick = { selectedSrc = src },
                            label = { Text(src) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                                selectedLabelColor = Emerald500
                            )
                        )
                    }
                }
                val sourceLabel = when (selectedSrc) {
                    "Bank" -> "Which bank?"; "Investment" -> "Which investment?"
                    "Debts" -> "Which debt / loan?"; "Custom" -> "Custom source name"; else -> ""
                }
                if (sourceLabel.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SoftField(sourceDetailName, sourceLabel) { sourceDetailName = it }
                }

                Spacer(Modifier.height(20.dp))

                // ── Date pill ─────────────────────────────────────────────────
                DatePickerField(value = date, onValueChange = { date = it }, label = "Date")

                Spacer(Modifier.height(12.dp))
                SoftField(notes, "Notes (optional)") { notes = it }

                Spacer(Modifier.height(24.dp))

                // ── Save ──────────────────────────────────────────────────────
                Button(
                    onClick = {
                        val finalAccount = when (selectedSrc) {
                            "Cash" -> "Cash in Hand"
                            "Custom" -> sourceDetailName
                            else -> sourceDetailName.ifEmpty { selectedSrc }
                        }
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
                    },
                    enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(if (isIncome) "Add Income" else "Add Expense",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun BasicAmountField(value: String, accent: androidx.compose.ui.graphics.Color, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("₹", fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        BasicTextFieldAmount(value, accent, onChange)
    }
}

@Composable
private fun BasicTextFieldAmount(value: String, accent: androidx.compose.ui.graphics.Color, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
        singleLine = true,
        decorationBox = { inner ->
            if (value.isBlank()) Text("0", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            inner()
        }
    )
}

@Composable
private fun SoftField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedLabelColor = Emerald500,
            cursorColor = Emerald500
        )
    )
}
