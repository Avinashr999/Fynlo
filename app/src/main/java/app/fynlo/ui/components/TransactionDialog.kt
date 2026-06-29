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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Transaction
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.SemanticRed
import app.fynlo.ui.theme.TemplateSegmentedSelector
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (Transaction) -> Unit,
    initialIsIncome: Boolean = false,
    allowTypeSwitch: Boolean = true,
    // C04: optional recency hooks. Default no-ops keep the dialog
    // testable and previewable without a ViewModel; production call
    // sites wire `viewModel::rememberLastTransactionCategory` and
    // `viewModel::recordTransactionCategory` to enable the smart
    // defaults behaviour. The boolean argument is `isIncome` so the
    // tracker can scope category recency by transaction type — same
    // boundary C05 enforces on the chip list itself.
    rememberLastCategory: suspend (Boolean) -> String? = { null },
    onRecordCategory: (Boolean, String) -> Unit = { _, _ -> },
    // 3.2.59 — orphan-account bug fix. Before this, the Bank / Investment /
    // Debts source chip surfaced a free-text "Which bank?" input. Users
    // could type "hdfc" while their account was named "HDFC Bank"; the
    // transaction saved fine, but `dao.updateAccountBalance(name=hdfc, ...)`
    // matched zero rows so the balance never changed. The transaction
    // became an orphan visible in history and the budget (because the
    // budget joins on category) but invisible in account balances and net
    // worth. Surfacing the actual lists here drives an ExposedDropdownMenu
    // so the user picks an existing entity by default; "Create new..."
    // is appended so genuinely-new accounts/investments/debts can still
    // be created. Defaults are empty so test/preview call sites still
    // construct cleanly — they just see the free-text fallback.
    bankAccounts:    List<String> = emptyList(),
    investmentNames: List<String> = emptyList(),
    debtNames:       List<String> = emptyList(),
    existingTransactions: List<Transaction> = emptyList(),
    // C13 #5 (3.2.81) — "Repeat monthly?" toggle on the Add dialog. When
    // ON, the caller's onRepeatMonthly callback fires alongside onConfirm
    // with the same transaction, letting the call site insert a parallel
    // RecurringTransaction template (day-of-month derived from the txn
    // date). Default no-op for tests / call sites that haven't wired it.
    onRepeatMonthly: (Transaction) -> Unit = { _ -> },
    currencyCode: String = "INR",
) {
    var isIncome by remember { mutableStateOf(initialIsIncome) }
    var amount by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    // C13 #7 (3.2.81) — Tags. The `Transaction.tags` column has existed
    // since the entity was first defined; just no UI to read/write it.
    // Free-text, comma-separated convention so the same column can power
    // a future tag-filter pill row without migration.
    var tags by remember { mutableStateOf("") }
    // C13 #5 (3.2.81) — Recurring toggle state.
    var repeatMonthly by remember { mutableStateOf(false) }

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
    var submitting by remember { mutableStateOf(false) }
    // C04 (subsuming C05's reset, completed by Stage 2.5): on every toggle
    // flip — and on initial open — ask the recency layer for the user's
    // most-recently-used category for the new type. Three cases:
    //   1. No recency yet (fresh install or first time using this type)
    //      → leave both fields blank so the user picks fresh.
    //   2. Recent value is in the curated chip list (`Categories.INCOME`
    //      / `Categories.EXPENSE`) → pre-select the chip, clear any
    //      lingering `customCategory` from a previous toggle flip.
    //   3. Recent value is a Custom-typed string (e.g. user typed
    //      "Charity") → set `selectedCategory = "Custom"` AND restore
    //      `customCategory = recent` together so the user sees their
    //      previously-typed value re-rendered in the text input below
    //      the chip row. This is the Stage 2.5 fix; without it the
    //      Custom-path recency was silently dropped.
    LaunchedEffect(initialIsIncome, allowTypeSwitch) {
        if (!allowTypeSwitch) isIncome = initialIsIncome
    }

    LaunchedEffect(isIncome) {
        val recent = rememberLastCategory(isIncome)
        when {
            recent == null -> {
                selectedCategory = ""
                customCategory = ""
            }
            recent in categories -> {
                selectedCategory = recent
                customCategory = ""
            }
            else -> {
                selectedCategory = "Custom"
                customCategory = recent
            }
        }
    }

    val sources = listOf("Cash", "Bank", "Investment", "Debts", "Custom")
    var selectedSrc by remember { mutableStateOf(sources[0]) }
    var sourceDetailName by remember { mutableStateOf("") }

    val accent = if (isIncome) Emerald500 else SemanticRed
    val dialogTitle = when {
        allowTypeSwitch -> "Add Transaction"
        isIncome -> "Add Income"
        else -> "Add Expense"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 5.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(10.dp))
                // ── Header ────────────────────────────────────────────────────
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(dialogTitle,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                Spacer(Modifier.height(16.dp))

                // ── Expense / Income toggle ───────────────────────────────────
                if (allowTypeSwitch) {
                    TemplateSegmentedSelector(
                        options = listOf("Expense", "Income"),
                        selectedIndex = if (isIncome) 1 else 0,
                        onSelected = { index -> isIncome = index == 1 },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // ── Big amount input (hero) ───────────────────────────────────
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    BasicAmountField(amount, accent, CurrencyUtils.symbolFor(currencyCode)) {
                        amount = it.filter { c -> c.isDigit() || c == '.' }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Category chips ────────────────────────────────────────────
                Text("Category", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FynloChoiceDropdown(
                    label = "Choose category",
                    options = categories,
                    selected = selectedCategory,
                    onPick = { selectedCategory = it },
                    placeholder = if (isIncome) "Select income type" else "Select expense type",
                )
                if (selectedCategory == "Custom") {
                    Spacer(Modifier.height(10.dp))
                    SoftField(customCategory, "Custom category name") { customCategory = it }
                }

                Spacer(Modifier.height(20.dp))

                // ── Account chips ─────────────────────────────────────────────
                Text(if (isIncome) "Deposit to" else "Pay from",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FynloChoiceDropdown(
                    label = if (isIncome) "Deposit to" else "Pay from",
                    options = sources,
                    selected = selectedSrc,
                    onPick = {
                        if (selectedSrc != it) sourceDetailName = ""
                        selectedSrc = it
                    },
                )
                val sourceLabel = when (selectedSrc) {
                    "Bank" -> "Which bank?"; "Investment" -> "Which investment?"
                    "Debts" -> "Which debt / loan?"; "Custom" -> "Custom source name"; else -> ""
                }
                // 3.2.59 — for Bank / Investment / Debts, pick the right
                // backing list and render a dropdown of existing entities
                // plus a sentinel "Create new..." entry. Custom stays as
                // free-text (it's the affordance for user-supplied names).
                // When the relevant list is empty (fresh install, no
                // accounts/investments/debts yet) the dropdown collapses
                // to free-text so the user can still create one inline.
                val sourceList: List<String> = when (selectedSrc) {
                    "Bank"       -> bankAccounts
                    "Investment" -> investmentNames
                    "Debts"      -> debtNames
                    else         -> emptyList()
                }
                if (sourceLabel.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    if (sourceList.isNotEmpty() && selectedSrc != "Custom") {
                        SourceDropdown(
                            label    = sourceLabel,
                            options  = sourceList,
                            selected = sourceDetailName,
                            onPick   = { sourceDetailName = it },
                        )
                    } else {
                        SoftField(sourceDetailName, sourceLabel) { sourceDetailName = it }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Date pill ─────────────────────────────────────────────────
                DatePickerField(value = date, onValueChange = { date = it }, label = "Date")

                Spacer(Modifier.height(12.dp))
                SoftField(notes, "Notes (optional)") { notes = it }

                // C13 #7 (3.2.81) — Tags field. Comma-separated, free-text
                // by design — keeps the data model identical to legacy
                // exports and lets the user paste from notes apps.
                Spacer(Modifier.height(12.dp))
                SoftField(tags, "Tags (comma-separated, optional)") { tags = it }

                // C13 #5 (3.2.81) — Repeat monthly toggle. Auto-derives
                // dayOfMonth from the txn date (clamped to 1..31). When the
                // user picks a date late in February (e.g. the 28th) the
                // RecurringWorker's last-day clamp at run time still does
                // the right thing for shorter months.
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Repeat monthly?",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "Also save as a recurring template (fires on day ${runCatching { DateUtils.parseInput(date).takeLast(2).toInt() }.getOrDefault(1)} each month)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = repeatMonthly,
                        onCheckedChange = { repeatMonthly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor    = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor    = Emerald500,
                            uncheckedThumbColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor  = MaterialTheme.colorScheme.surface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }

                val previewAmount = amount.toDoubleOrNull() ?: 0.0
                val previewAccount = when (selectedSrc) {
                    "Cash" -> "Personal Cash"
                    "Custom" -> sourceDetailName
                    else -> sourceDetailName.ifEmpty { selectedSrc }
                }
                val previewCategory =
                    if (selectedCategory == "Custom") customCategory.trim() else selectedCategory.trim()
                val parsedPreviewDate = remember(date) {
                    runCatching { DateUtils.parseInput(date) }.getOrDefault(date)
                }
                val duplicateWarning = remember(
                    amount,
                    isIncome,
                    previewCategory,
                    previewAccount,
                    parsedPreviewDate,
                    existingTransactions,
                ) {
                    val amt = amount.toDoubleOrNull() ?: return@remember null
                    if (amt <= 0.0 || previewCategory.isBlank() || previewAccount.isBlank()) return@remember null
                    val txType = if (isIncome) "income" else "expense"
                    existingTransactions.firstOrNull { txn ->
                        txn.type.equals(txType, ignoreCase = true) &&
                            txn.date == parsedPreviewDate &&
                            txn.category.equals(previewCategory, ignoreCase = true) &&
                            kotlin.math.abs(txn.amount - amt) <= 0.005 &&
                            if (isIncome) {
                                txn.toAcct.equals(previewAccount, ignoreCase = true)
                            } else {
                                txn.fromAcct.equals(previewAccount, ignoreCase = true)
                            }
                    }?.let { "A similar $txType already exists on this date. Save only if this is a separate entry." }
                }
                if (previewAmount > 0.0 && previewAccount.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    AccountImpactPreview(
                        lines = if (isIncome) {
                            listOf("$previewAccount +${CurrencyFormatter.detail(previewAmount, currencyCode)}")
                        } else {
                            listOf("$previewAccount -${CurrencyFormatter.detail(previewAmount, currencyCode)}")
                        },
                    )
                }
                if (duplicateWarning != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    ) {
                        Text(
                            duplicateWarning,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Save ──────────────────────────────────────────────────────
                Button(
                    onClick = {
                        if (submitting) return@Button
                        submitting = true
                        val finalAccount = when (selectedSrc) {
                            "Cash" -> "Personal Cash"
                            "Custom" -> sourceDetailName
                            else -> sourceDetailName.ifEmpty { selectedSrc }
                        }
                        val finalCategory =
                            if (selectedCategory == "Custom") customCategory else selectedCategory
                        val txn = Transaction(
                            id = app.fynlo.logic.Ids.newId(),
                            date = DateUtils.parseInput(date),
                            type = if (isIncome) "Income" else "Expense",
                            amount = amount.toDoubleOrNull() ?: 0.0,
                            category = finalCategory,
                            desc = desc,
                            notes = notes,
                            tags = tags.trim(),
                            fromAcct = if (isIncome) "" else finalAccount,
                            toAcct = if (isIncome) finalAccount else ""
                        )
                        // C04: record this category as the most-recently-used for the
                        // current transaction type so the NEXT Add Transaction pre-selects
                        // it. Records the FINAL value (e.g., "Charity" for a Custom pick),
                        // not the chip-list sentinel ("Custom"). Default no-op lambda
                        // means dialogs constructed without the recency wiring (tests,
                        // previews) still submit cleanly.
                        onRecordCategory(isIncome, finalCategory)
                        onConfirm(txn)
                        // C13 #5 — also fire the recurring callback so the
                        // call site can create the matching template.
                        if (repeatMonthly) onRepeatMonthly(txn)
                    },
                    // C17 (3.2.42) — was just enabled-on-amount-positive;
                    // now uses a per-field disabledReason so the user sees
                    // *what* needs filling rather than staring at a greyed
                    // Add button.
                    enabled = run {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        amt > 0.0 && selectedCategory.isNotBlank() &&
                            (selectedCategory != "Custom" || customCategory.isNotBlank()) &&
                            !submitting
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                ) {
                    Text(if (isIncome) "Add Income" else "Add Expense",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                run {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val reason: String? = when {
                        amt <= 0.0                                            -> "Enter an amount to continue"
                        selectedCategory.isBlank()                            -> "Pick a category to continue"
                        selectedCategory == "Custom" && customCategory.isBlank() -> "Type a custom category to continue"
                        else                                                  -> null
                    }
                    DisabledButtonHint(reason)
                }
            }
        }
    }
}
}

@Composable
private fun BasicAmountField(
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    currencySymbol: String,
    onChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(currencySymbol, fontSize = 32.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        BasicTextFieldAmount(value, accent, onChange)
    }
}

@Composable
fun AccountImpactPreview(lines: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Balance impact",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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

/**
 * 3.2.59 — orphan-account fix. Replaces the free-text bank / investment /
 * debt input with a dropdown of existing entities so the typed name
 * can't drift away from the canonical account.name and silently break
 * the balance-update WHERE clause.
 *
 * - When the user has at least one matching entity, the dropdown lists
 *   them with the first option pre-selected on open.
 * - A trailing "+ Create new…" sentinel reveals a free-text input so
 *   genuinely-new entities can still be created inline.
 * - Custom is intentionally not routed here — Custom is the affordance
 *   for arbitrary names (e.g. "Friend Vikas") so free-text is correct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    val createNew = "+ Create new…"
    var expanded by remember { mutableStateOf(false) }
    var createMode by remember(options) {
        // If the current value isn't in the option list AND isn't blank,
        // the caller is editing an entry that pre-dates the dropdown
        // (e.g. an orphan transaction's free-text name). Start in create
        // mode so they can keep the typed value visible.
        mutableStateOf(selected.isNotBlank() && selected !in options)
    }
    // Auto-fill the first option when nothing's selected yet so the
    // user can tap Save immediately and still get a valid account.
    LaunchedEffect(options) {
        if (!createMode && selected.isBlank() && options.isNotEmpty()) {
            onPick(options.first())
        }
    }

    if (createMode) {
        SoftField(selected, label, onPick)
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { createMode = false; if (options.isNotEmpty()) onPick(options.first()) }) {
            Text("Pick from existing", style = MaterialTheme.typography.labelSmall)
        }
    } else {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value         = selected.ifBlank { options.firstOrNull().orEmpty() },
                onValueChange = {},
                readOnly      = true,
                label         = { Text(label) },
                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine    = true,
                shape         = RoundedCornerShape(16.dp),
                modifier      = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    focusedBorderColor      = Emerald500,
                    unfocusedBorderColor    = androidx.compose.ui.graphics.Color.Transparent,
                    focusedLabelColor       = Emerald500,
                    cursorColor             = Emerald500
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text    = { Text(opt) },
                        onClick = { onPick(opt); expanded = false }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text    = { Text(createNew, color = Emerald500) },
                    onClick = { onPick(""); createMode = true; expanded = false }
                )
            }
        }
    }
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
