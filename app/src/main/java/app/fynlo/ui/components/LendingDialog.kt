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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLendingDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Borrower, String) -> Unit,
    initialBorrower: Borrower?= null,
    currencyCode: String = "INR",
) {
    val people by viewModel.people.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val locale = LocalLocale.current.platformLocale

    var selectedPerson by remember { mutableStateOf<Person?>(null) }
    var borrowerExpanded by remember { mutableStateOf(false) }
    // v3.3.0: edit shows the exact stored amount/rate (no silent rounding on Save).
    var amount by remember { mutableStateOf(CurrencyFormatter.plainInput(initialBorrower?.amount ?: 0.0)) }
    var rate by remember { mutableStateOf(CurrencyFormatter.plainInput(initialBorrower?.rate ?: 0.0)) }
    // New amounts are whole rupees; an old loan saved with paise stays editable as-is.
    val amountAllowsPaise = remember { amount.contains('.') }
    var date by remember { mutableStateOf(initialBorrower?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var due by remember { mutableStateOf(initialBorrower?.due?.let { DateUtils.formatToDisplay(it) } ?: "") }
    var notes by remember { mutableStateOf(initialBorrower?.notes ?: "") }
    var selectedType by remember { mutableStateOf(initialBorrower?.intType ?: "Simple Interest") }
    var stopInterestAfterDue by remember { mutableStateOf(initialBorrower?.stopInterestAfterDue ?: false) }

    val accountOptions = if (accounts.isNotEmpty()) accounts
        else listOf(app.fynlo.data.model.Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))
    var selectedAccount by remember(accountOptions, initialBorrower?.id) {
        mutableStateOf(
            accountOptions.firstOrNull { it.name == initialBorrower?.sourceAccount }
                ?: accountOptions.first()
        )
    }
    var accountExpanded by remember { mutableStateOf(false) }

    val advancedInterestTypes = listOf("Reducing Balance", "Compound Interest") // lean v1: no new "Both"
    val isEdit = initialBorrower != null
    var submitting by remember(initialBorrower?.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 6.dp
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        modifier = Modifier
                            .size(width = 44.dp, height = 5.dp)
                            .align(Alignment.CenterHorizontally),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(99.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ) {}
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(if (isEdit) "Edit Loan" else "New Loan",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    }
                Spacer(Modifier.height(16.dp))

                // -- Amount hero -----------------------------------------------
                AmountHero(amount, currencyCode, amountAllowsPaise) { amount = it }
                Spacer(Modifier.height(24.dp))

                // -- Borrower --------------------------------------------------
                Text("Borrower", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (isEdit) {
                    Text(requireNotNull(initialBorrower).name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                } else if (people.isEmpty()) {
                    Text("Add a contact in Contact Book first.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = borrowerExpanded,
                        onExpandedChange = { borrowerExpanded = !borrowerExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedPerson?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Borrower") },
                            placeholder = { Text("Select from contacts") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = borrowerExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = borrowerExpanded,
                            onDismissRequest = { borrowerExpanded = false },
                        ) {
                            people.forEach { person ->
                                DropdownMenuItem(
                                    text = { Text(person.name) },
                                    onClick = {
                                        selectedPerson = person
                                        borrowerExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(if (isEdit) "Lent from" else "Lend from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedAccount.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isEdit) "Lent from" else "Lend from") },
                        supportingText = {
                            Text(
                                "${selectedAccount.type}  -  Balance: ${CurrencyFormatter.exact(selectedAccount.balance, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Emerald500,
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false },
                    ) {
                        accountOptions.forEach { acct ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        Arrangement.SpaceBetween,
                                        Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(acct.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                acct.type,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            CurrencyFormatter.exact(acct.balance, currencyCode, locale),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                onClick = {
                                    selectedAccount = acct
                                    accountExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // -- Interest type ---------------------------------------------
                Text("Interest type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                // Lean personal: Simple / Compound / Reducing free on lend (same as debt).
                val interestOptions = remember(selectedType) {
                    val base = listOf("Simple Interest") + advancedInterestTypes
                    // Legacy "Both" rows stay editable/visible; new loans cannot pick it.
                    if (selectedType == "Both" && "Both" !in base) base + "Both" else base
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
                if (selectedType == "Compound Interest") {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Interest compounds monthly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))
                LendSoftField(rate, "Annual interest rate (%)", KeyboardType.Decimal) { rate = decimalOnly(it) }
                Spacer(Modifier.height(12.dp))
                DatePickerField(value = date, onValueChange = { date = it }, label = "Lending date")
                Spacer(Modifier.height(12.dp))
                DatePickerField(value = due, onValueChange = { due = it }, label = "Due date", optional = true)
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Stop interest after due date", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Use when you give grace time and do not want extra interest after the due date.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = stopInterestAfterDue, onCheckedChange = { stopInterestAfterDue = it })
                    }
                }
                Spacer(Modifier.height(12.dp))
                LendSoftField(notes, "Notes (optional)", KeyboardType.Text) { notes = it }

                Spacer(Modifier.height(24.dp))

                FormPrimaryButton(
                    text = if (isEdit) "Save changes" else "Add loan",
                    onClick = {
                        if (submitting) return@FormPrimaryButton
                        submitting = true
                        val finalSource = selectedAccount.name
                        val rawId = initialBorrower?.id ?: ""
                        val now = System.currentTimeMillis()
                        val finalId = if (rawId.isBlank()) app.fynlo.logic.Ids.newId() else rawId
                        val borrower = (initialBorrower ?: Borrower(
                            id = finalId,
                            name = selectedPerson?.name ?: "Unknown",
                            amount = 0.0,
                            rate = 0.0,
                            date = DateUtils.parseInput(date),
                        )).copy(
                            id = finalId,
                            sourceAccount = selectedAccount.name,
                            stopInterestAfterDue = stopInterestAfterDue,
                            name = selectedPerson?.name ?: initialBorrower?.name ?: "Unknown",
                            phone = selectedPerson?.phone ?: initialBorrower?.phone ?: "",
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            rate = rate.toDoubleOrNull() ?: 0.0,
                            date = DateUtils.parseInput(date),
                            due = if (due.isNotEmpty()) DateUtils.parseInput(due) else "",
                            intType = selectedType,
                            status = initialBorrower?.status ?: "Active",
                            notes = notes,
                            updatedAt = now,
                            createdAt = initialBorrower?.createdAt ?: now,
                        )
                        onConfirm(borrower, finalSource)
                    },
                    enabled = (selectedPerson != null || isEdit) && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && !submitting,
                )
                // C17 (3.2.42) - surface which field is blocking the Add.
                run {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val reason: String?= when {
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

}

@Composable
internal fun LendSoftField(value: String, label: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
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

/** Digits with at most one decimal point (rates like 13.5). */
internal fun decimalOnly(raw: String): String {
    val kept = raw.filter { it.isDigit() || it == '.' }
    val dot = kept.indexOf('.')
    return if (dot < 0) kept else kept.substring(0, dot + 1) + kept.substring(dot + 1).replace(".", "")
}

/** Big centred amount entry shared by the new-loan and new-debt forms. */
@Composable
internal fun AmountHero(amount: String, currencyCode: String, allowPaise: Boolean, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth(), Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CurrencyUtils.symbolFor(currencyCode), fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = amount,
                onValueChange = { onChange(if (allowPaise) decimalOnly(it) else it.filter { c -> c.isDigit() }) },
                textStyle = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                keyboardOptions = KeyboardOptions(keyboardType = if (allowPaise) KeyboardType.Decimal else KeyboardType.Number),
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
}
