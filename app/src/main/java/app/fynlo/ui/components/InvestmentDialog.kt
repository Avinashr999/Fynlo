package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Account
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Investment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import java.util.*
import app.fynlo.ui.theme.*

// ─── What the caller receives on confirm ────────────────────────────────────

data class InvestmentSaveRequest(
    val investment: Investment,
    val sourceType: String,        // "account" | "existing_debt" | "new_loan"
    val sourceAccountName: String = "",
    val sourceDebt: Debt? = null,  // existing debt selected
    val newLoan: Debt? = null      // auto-created if sourceType = "new_loan"
)

// ─── Source types ────────────────────────────────────────────────────────────

private val SOURCE_ACCOUNT       = "account"
private val SOURCE_EXISTING_DEBT = "existing_debt"
private val SOURCE_NEW_LOAN      = "new_loan"

// ─── Dialog ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInvestmentDialog(
    accounts: List<Account> = emptyList(),
    debts: List<Debt> = emptyList(),
    currencyCode: String = "INR",
    onDismiss: () -> Unit,
    onConfirm: (InvestmentSaveRequest) -> Unit,
    initialInvestment: Investment? = null
) {
    val currencySymbol = CurrencyUtils.symbolFor(currencyCode)
    val isNew = initialInvestment == null || initialInvestment.id.isBlank()

    var name     by remember { mutableStateOf(initialInvestment?.name     ?: "") }
    var amount   by remember { mutableStateOf(initialInvestment?.invested?.let { if (it > 0) CurrencyFormatter.input(it) else "" } ?: "") }
    var date     by remember { mutableStateOf(initialInvestment?.date?.let { DateUtils.formatToDisplay(it) } ?: java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var type     by remember { mutableStateOf(initialInvestment?.type     ?: "Stocks") }
    var notes    by remember { mutableStateOf(initialInvestment?.notes    ?: "") }

    val investTypes = listOf("Stocks", "Mutual Funds", "Gold", "Fixed Deposit", "Business", "Real Estate", "Crypto", "Other")
    var typeExpanded by remember { mutableStateOf(false) }

    // ── Funding source state (only for new investments) ──────────────────────
    var sourceType by remember { mutableStateOf(SOURCE_ACCOUNT) }

    // Account picker
    val activeAccounts = accounts
    var selectedAccount by remember(activeAccounts) {
        mutableStateOf(activeAccounts.firstOrNull())
    }
    var accountExpanded by remember { mutableStateOf(false) }

    // Existing debt picker
    val activeDebts = debts.filter { it.status == "Active" }
    var selectedDebt by remember(activeDebts) {
        mutableStateOf(activeDebts.firstOrNull())
    }
    var debtExpanded by remember { mutableStateOf(false) }

    // New loan fields
    var lenderName   by remember { mutableStateOf("") }
    var loanAmount   by remember { mutableStateOf("") }
    var loanRate     by remember { mutableStateOf("") }
    var loanIntType  by remember { mutableStateOf("Simple Interest") }
    var loanDue      by remember { mutableStateOf("") }
    var loanTypeExpanded by remember { mutableStateOf(false) }

    // Keep loanAmount in sync with invested amount when user types
    LaunchedEffect(amount) {
        if (loanAmount.isBlank() || loanAmount == "0") loanAmount = amount
    }

    val amountDouble = amount.toDoubleOrNull() ?: 0.0
    // C17 (3.2.42) — compute the disabled reason first; canSave just checks
    // the reason for null. Lets us surface which field is missing inline.
    val disabledReason: String? = when {
        name.isBlank()        -> "Enter an investment name to continue"
        amountDouble <= 0.0   -> "Enter the invested amount to continue"
        sourceType == SOURCE_ACCOUNT && selectedAccount == null && activeAccounts.isNotEmpty() ->
            "Pick a source account to continue"
        sourceType == SOURCE_EXISTING_DEBT && selectedDebt == null ->
            "Pick the debt that funded this investment"
        sourceType == SOURCE_NEW_LOAN && lenderName.isBlank() ->
            "Enter the lender's name to continue"
        else -> null
    }
    val canSave = disabledReason == null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    if (isNew) "Add Investment" else "Edit Investment",
                    style = MaterialTheme.typography.headlineSmall
                )

                // Asset name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Asset Name") },
                    placeholder = { Text("e.g. HDFC Nifty ETF, SGB 2025…") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Asset type
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = !typeExpanded }) {
                    OutlinedTextField(
                        value = type, onValueChange = {}, readOnly = true,
                        label = { Text("Asset Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        investTypes.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { type = t; typeExpanded = false })
                        }
                    }
                }

                // Invested amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Invested ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date
                DatePickerField(value = date, onValueChange = { date = it }, label = "Investment Date")

                // Notes
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Funding Source — only for new investments ─────────────────
                if (isNew) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        "Where did this money come from?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    // Radio group
                    listOf(
                        SOURCE_ACCOUNT       to "From my account",
                        SOURCE_EXISTING_DEBT to "From an existing loan I already track",
                        SOURCE_NEW_LOAN      to "Took a new loan for this investment"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = sourceType == value,
                                    onClick = { sourceType = value },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sourceType == value, onClick = { sourceType = value })
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // ── Account picker ────────────────────────────────────────
                    if (sourceType == SOURCE_ACCOUNT) {
                        if (activeAccounts.isEmpty()) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text("No accounts set up yet. Add accounts first for full money tracking.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            ExposedDropdownMenuBox(expanded = accountExpanded, onExpandedChange = { accountExpanded = !accountExpanded }) {
                                OutlinedTextField(
                                    value = selectedAccount?.name ?: "",
                                    onValueChange = {}, readOnly = true,
                                    label = { Text("Deduct from which account?") },
                                    supportingText = selectedAccount?.let { acct ->
                                        { Text("${acct.type}  •  Balance: ${CurrencyFormatter.detail(acct.balance, currencyCode)}", style = MaterialTheme.typography.labelSmall) }
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                                    activeAccounts.forEach { acct ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            when (acct.type.lowercase()) { "cash" -> Icons.Default.Wallet; "upi" -> Icons.Default.MonetizationOn; else -> Icons.Default.AccountBalance },
                                                            null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        Column {
                                                            Text(acct.name, fontWeight = FontWeight.Medium)
                                                            Text(acct.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Text(
                                                        CurrencyFormatter.detail(acct.balance, currencyCode),
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            },
                                            onClick = { selectedAccount = acct; accountExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Existing debt picker ──────────────────────────────────
                    if (sourceType == SOURCE_EXISTING_DEBT) {
                        if (activeDebts.isEmpty()) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text("No active loans found. Add a debt first, or choose 'Took a new loan'.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            ExposedDropdownMenuBox(expanded = debtExpanded, onExpandedChange = { debtExpanded = !debtExpanded }) {
                                OutlinedTextField(
                                    value = selectedDebt?.name ?: "",
                                    onValueChange = {}, readOnly = true,
                                    label = { Text("Which loan funded this?") },
                                    supportingText = selectedDebt?.let { d ->
                                        { Text("Outstanding: ${CurrencyFormatter.detail(d.amount - d.paid, currencyCode)}", style = MaterialTheme.typography.labelSmall) }
                                    },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debtExpanded) },
                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                )
                                ExposedDropdownMenu(expanded = debtExpanded, onDismissRequest = { debtExpanded = false }) {
                                    activeDebts.forEach { d ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(d.name, fontWeight = FontWeight.Medium)
                                                    Text(CurrencyFormatter.detail(d.amount - d.paid, currencyCode), color = MaterialTheme.colorScheme.error)
                                                }
                                            },
                                            onClick = { selectedDebt = d; debtExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── New loan fields ───────────────────────────────────────
                    if (sourceType == SOURCE_NEW_LOAN) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "New loan details — will be auto-added to your Debt section",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                OutlinedTextField(
                                    value = lenderName, onValueChange = { lenderName = it },
                                    label = { Text("Lender / Bank name") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = loanAmount, onValueChange = { loanAmount = it },
                                    label = { Text("Loan Amount ($currencySymbol)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = loanRate, onValueChange = { loanRate = it },
                                        label = { Text("Interest Rate %") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ExposedDropdownMenuBox(
                                        expanded = loanTypeExpanded,
                                        onExpandedChange = { loanTypeExpanded = !loanTypeExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = loanIntType, onValueChange = {}, readOnly = true,
                                            label = { Text("Type") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = loanTypeExpanded) },
                                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(expanded = loanTypeExpanded, onDismissRequest = { loanTypeExpanded = false }) {
                                            listOf("Simple Interest", "Compound Interest").forEach { t ->
                                                DropdownMenuItem(text = { Text(t) }, onClick = { loanIntType = t; loanTypeExpanded = false })
                                            }
                                        }
                                    }
                                }
                                DatePickerField(value = loanDue, onValueChange = { loanDue = it }, label = "Due Date (optional)")
                            }
                        }
                    }

                    // ── Info note ─────────────────────────────────────────────
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Current market value can be updated anytime via the 'Update Value' button on each investment card.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Buttons ───────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedDate = DateUtils.parseInput(date)
                            val investment = Investment(
                                id         = initialInvestment?.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                                name       = name.trim(),
                                type       = type,
                                invested   = amountDouble,
                                currentVal = amountDouble, // starts equal; user updates via 'Update Value'
                                date       = parsedDate,
                                notes      = notes.trim()
                            )

                            if (!isNew) {
                                onConfirm(InvestmentSaveRequest(investment, sourceType = initialInvestment?.sourceType ?: ""))
                                return@Button
                            }

                            val req = when (sourceType) {
                                SOURCE_ACCOUNT -> InvestmentSaveRequest(
                                    investment = investment,
                                    sourceType = SOURCE_ACCOUNT,
                                    sourceAccountName = selectedAccount?.name ?: "Cash"
                                )
                                SOURCE_EXISTING_DEBT -> InvestmentSaveRequest(
                                    investment = investment,
                                    sourceType = SOURCE_EXISTING_DEBT,
                                    sourceDebt = selectedDebt
                                )
                                SOURCE_NEW_LOAN -> {
                                    val loanAmt = loanAmount.toDoubleOrNull() ?: amountDouble
                                    val newDebt = Debt(
                                        id      = UUID.randomUUID().toString(),
                                        name    = lenderName.trim(),
                                        type    = "Bank / NBFC Loan",
                                        amount  = loanAmt,
                                        rate    = loanRate.toDoubleOrNull() ?: 0.0,
                                        intType = loanIntType,
                                        date    = parsedDate,
                                        due     = DateUtils.parseInput(loanDue).takeIf { loanDue.isNotBlank() } ?: "",
                                        notes   = "Auto-created: funded investment in ${name.trim()}"
                                    )
                                    InvestmentSaveRequest(investment = investment, sourceType = SOURCE_NEW_LOAN, newLoan = newDebt)
                                }
                                else -> InvestmentSaveRequest(investment, sourceType = "")
                            }
                            onConfirm(req)
                        },
                        enabled = canSave
                    ) {
                        Text(if (isNew) "Save Investment" else "Update")
                    }
                }
                DisabledButtonHint(disabledReason)
            }
        }
    }
}
