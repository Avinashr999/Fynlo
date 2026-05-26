package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Person
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.Emerald500
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLendingDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Borrower, String) -> Unit,
    initialBorrower: Borrower? = null,
    currencyCode: String = "INR",
) {
    val people by viewModel.people.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val locale = Locale.getDefault()

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var amount by remember { mutableStateOf(initialBorrower?.amount?.takeIf { it > 0 }?.let { String.format("%.0f", it) } ?: "") }
    var rate by remember { mutableStateOf(initialBorrower?.rate?.takeIf { it > 0 }?.let { String.format("%.0f", it) } ?: "") }
    var date by remember { mutableStateOf(initialBorrower?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialBorrower?.due?.let { DateUtils.formatToDisplay(it) } ?: "") }
    var notes by remember { mutableStateOf(initialBorrower?.notes ?: "") }
    val isPro by app.fynlo.billing.BillingManager.isPro.collectAsState()
    var selectedType by remember { mutableStateOf(initialBorrower?.type ?: "Simple Interest") }
    var showAdvancedInterest by remember { mutableStateOf(selectedType != "Simple Interest") }

    val accountOptions = if (accounts.isNotEmpty()) accounts
        else listOf(app.fynlo.data.model.Account(id = "cash", name = "Cash in Hand", type = "Cash", balance = 0.0))
    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    val advancedInterestTypes = listOf("Reducing Balance", "Compound Interest", "Both")
    val isEdit = initialBorrower != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 20.dp).imePadding(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(if (isEdit) "Edit Loan" else "New Loan",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                Spacer(Modifier.height(16.dp))

                // ── Amount hero ───────────────────────────────────────────────
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(CurrencyUtils.symbolFor(currencyCode), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = amount,
                            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                            textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(Emerald500),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (amount.isBlank()) Text("0", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                inner()
                            }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                // ── Borrower ──────────────────────────────────────────────────
                Text("Borrower", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (isEdit) {
                    Text(initialBorrower!!.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                } else if (people.isEmpty()) {
                    Text("Add a contact in Contact Book first.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        people.forEach { person ->
                            FilterChip(
                                selected = selectedPerson?.id == person.id,
                                onClick = { selectedPerson = person },
                                label = { Text(person.name) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                                    selectedLabelColor = Emerald500)
                            )
                        }
                    }
                }

                if (!isEdit) {
                    Spacer(Modifier.height(20.dp))
                    Text("Lend from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        accountOptions.forEach { acct ->
                            FilterChip(
                                selected = selectedAccount.id == acct.id,
                                onClick = { selectedAccount = acct },
                                label = { Text(acct.name) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                                    selectedLabelColor = Emerald500)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Balance: ${CurrencyFormatter.detail(selectedAccount.balance, currencyCode, locale)}",
                        style = MaterialTheme.typography.labelSmall, color = Emerald500)
                }

                Spacer(Modifier.height(20.dp))

                // ── Interest type ─────────────────────────────────────────────
                Text("Interest type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedType == "Simple Interest",
                        onClick = { selectedType = "Simple Interest" },
                        label = { Text("Simple Interest") },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                            selectedLabelColor = Emerald500)
                    )
                    if (showAdvancedInterest) {
                        advancedInterestTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.16f),
                                    selectedLabelColor = Emerald500)
                            )
                        }
                    }
                }
                if (!showAdvancedInterest && isPro) {
                    TextButton(onClick = { showAdvancedInterest = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Advanced options", color = Emerald500, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(16.dp))
                LendSoftField(rate, "Annual interest rate (%)", KeyboardType.Number) { rate = it }
                Spacer(Modifier.height(12.dp))
                DatePickerField(value = date, onValueChange = { date = it }, label = "Lending date")
                Spacer(Modifier.height(12.dp))
                DatePickerField(value = due, onValueChange = { due = it }, label = "Due date", optional = true)
                Spacer(Modifier.height(12.dp))
                LendSoftField(notes, "Notes (optional)", KeyboardType.Text) { notes = it }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val finalSource = if (isEdit) "Cash in Hand" else selectedAccount.name
                        val rawId = initialBorrower?.id ?: ""
                        val borrower = Borrower(
                            id     = if (rawId.isBlank()) UUID.randomUUID().toString() else rawId,
                            sourceAccount = if (!isEdit) selectedAccount.name else initialBorrower!!.sourceAccount,
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
                    enabled = (selectedPerson != null || isEdit) && (amount.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(if (isEdit) "Save Changes" else "Add Loan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun LendSoftField(value: String, label: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor = Emerald500,
            unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = Emerald500,
            cursorColor = Emerald500
        )
    )
}
