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
import androidx.compose.ui.platform.LocalLocale
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
    val locale = LocalLocale.current.platformLocale

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var amount by remember { mutableStateOf(initialBorrower?.amount?.takeIf { it > 0 }?.let { String.format("%.0f", it) } ?: "") }
    var rate by remember { mutableStateOf(initialBorrower?.rate?.takeIf { it > 0 }?.let { String.format("%.0f", it) } ?: "") }
    var date by remember { mutableStateOf(initialBorrower?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialBorrower?.due?.let { DateUtils.formatToDisplay(it) } ?: "") }
    var notes by remember { mutableStateOf(initialBorrower?.notes ?: "") }
    val isPro by app.fynlo.billing.BillingManager.isPro.collectAsState()
    var selectedType by remember { mutableStateOf(initialBorrower?.intType ?: "Simple Interest") }

    val accountOptions = if (accounts.isNotEmpty()) accounts
        else listOf(app.fynlo.data.model.Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))
    var selectedAccount by remember { mutableStateOf(accountOptions.first()) }

    val advancedInterestTypes = listOf("Reducing Balance", "Compound Interest", "Both")
    val isEdit = initialBorrower != null
    var submitting by remember(initialBorrower?.id) { mutableStateOf(false) }

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
                // 3.2.26 — unified with DebtDialog's widget (audit consistency
                // surfaced during C12 Stage 1 smoke). Was a `FlowRow<FilterChip>`
                // with Simple Interest always visible and a `Pro`-gated
                // "Advanced options" TextButton that revealed Reducing / Compound
                // / SI+CI chips. Now: a single `ExposedDropdownMenuBox` matching
                // DebtDialog. Free vs Pro gating is preserved by varying the
                // dropdown's options — free users see only "Simple Interest";
                // Pro users see all 4. Eliminates the extra-tap "Advanced
                // options" affordance for Pro users (they already paid for
                // these options, no point hiding them) while still gating
                // free-tier users from the engine's advanced modes.
                //
                // Edge case: if a free user has a borrower previously saved
                // with an advanced type (Pro downgrade or admin override),
                // the field still displays it correctly via `InterestEngine.label`,
                // and the dropdown won't offer the advanced types — they
                // can only switch back to Simple Interest from there.
                Text("Interest type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val interestOptions = remember(isPro) {
                    if (isPro) listOf("Simple Interest") + advancedInterestTypes
                    else listOf("Simple Interest")
                }
                var interestExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = interestExpanded,
                    onExpandedChange = { interestExpanded = !interestExpanded },
                ) {
                    OutlinedTextField(
                        value = app.fynlo.logic.InterestEngine.label(selectedType),
                        onValueChange = {}, readOnly = true,
                        label = { Text("Interest Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = interestExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(expanded = interestExpanded, onDismissRequest = { interestExpanded = false }) {
                        interestOptions.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(app.fynlo.logic.InterestEngine.label(type)) },
                                onClick = {
                                    selectedType = type
                                    interestExpanded = false
                                },
                            )
                        }
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
                        if (submitting) return@Button
                        submitting = true
                        val finalSource = if (isEdit) "Personal Cash" else selectedAccount.name
                        val rawId = initialBorrower?.id ?: ""
                        val borrower = Borrower(
                            id     = if (rawId.isBlank()) app.fynlo.logic.Ids.newId() else rawId,
                            sourceAccount = if (!isEdit) selectedAccount.name else initialBorrower!!.sourceAccount,
                            name   = selectedPerson?.name ?: initialBorrower?.name ?: "Unknown",
                            phone  = selectedPerson?.phone ?: initialBorrower?.phone ?: "",
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            rate   = rate.toDoubleOrNull() ?: 0.0,
                            date   = DateUtils.parseInput(date),
                            due    = if (due.isNotEmpty()) DateUtils.parseInput(due) else "",
                            intType = selectedType,
                            status = initialBorrower?.status ?: "Active",
                            notes  = notes,
                            paid   = initialBorrower?.paid ?: 0.0
                        )
                        onConfirm(borrower, finalSource)
                    },
                    enabled = (selectedPerson != null || isEdit) && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && !submitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(if (isEdit) "Save Changes" else "Add Loan",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                // C17 (3.2.42) — surface which field is blocking the Add.
                run {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val reason: String? = when {
                        !isEdit && selectedPerson == null -> "Pick a borrower to continue"
                        amt <= 0.0                        -> "Enter the loan amount to continue"
                        else                              -> null
                    }
                    DisabledButtonHint(reason)
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
