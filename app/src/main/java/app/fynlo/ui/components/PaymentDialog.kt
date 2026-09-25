package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.logic.InterestPolicy
import java.util.*
import app.fynlo.ui.theme.*

// --- Collect Loan Repayment -------------------------------------------------

private fun preferredMoneyAccount(accountOptions: List<Account>): Account {
    val realAccounts = accountOptions.filterNot {
        it.name.equals("New", ignoreCase = true) || it.id.equals("new", ignoreCase = true)
    }
    val candidates = realAccounts.ifEmpty { accountOptions }
    return candidates
        .filter { it.type.equals("Cash", true) || it.type.equals("Bank", true) }
        .maxByOrNull { it.balance }
        ?: candidates.maxByOrNull { it.balance }
        ?: accountOptions.first()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectPaymentDialog(
    borrower: Borrower,
    payments: List<Payment> = emptyList(),
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (Payment, String) -> Unit,
    currencyCode: String = "INR",
) {
    val today = java.time.LocalDate.now()
    val locale = LocalLocale.current.platformLocale

    // Accrued interest keeps running from the loan date; collected interest is separate.
    // Lean v1 (Simple/Compound/Reducing): money block + preview from InterestPolicy paise helpers.
    val usePaise = InterestPolicy.usesPaiseMethod(borrower.intType)
    var date by remember { mutableStateOf(today.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    val paymentAsOf = remember(date) {
        runCatching { DateUtils.parseInput(date) }.getOrDefault("")
            .ifBlank { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    }
    val paiseBalances = remember(borrower, payments, usePaise, paymentAsOf) {
        if (usePaise) InterestPolicy.paiseBalancesForBorrower(borrower, paymentAsOf, payments) else null
    }
    val interestBreakdown = remember(borrower, payments) {
        if (payments.isEmpty()) {
            app.fynlo.logic.InterestPolicy.borrowerBreakdown(borrower)
        } else {
            app.fynlo.logic.InterestPolicy.borrowerBreakdown(borrower, payments)
        }
    }
    val accruedInterest = interestBreakdown.accrued
    // v3.3.1: one shared balance function (paise: at the payment date; legacy: today).
    val sharedBalance = remember(borrower, payments, usePaise, paymentAsOf) {
        if (usePaise) InterestPolicy.borrowerBalance(borrower, payments, paymentAsOf)
        else InterestPolicy.borrowerBalance(borrower, payments)
    }
    val interestOutstanding = sharedBalance.interestDue
    val principalOutstanding = sharedBalance.principal
    val totalOutstanding = interestOutstanding + principalOutstanding

    // Payment fields — lean uses a single Amount; legacy keeps Principal + Interest.
    var amountStr by remember { mutableStateOf("") }
    // When set, Amount tracks settlement/interest-only totals as the payment date changes.
    var leanAmountPreset by remember { mutableStateOf<String?>(null) } // "full" | "interest" | null
    var principalStr by remember { mutableStateOf("") }
    var interestStr  by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var interestAllocationType by remember { mutableStateOf(InterestPolicy.CURRENT_PERIOD_INTEREST) }

    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))
    val principalVal = principalStr.toDoubleOrNull() ?: 0.0
    val interestVal  = interestStr.toDoubleOrNull()  ?: 0.0
    val amountVal    = amountStr.toDoubleOrNull() ?: 0.0
    val totalAmount  = if (usePaise) amountVal else principalVal + interestVal
    val isValid      = totalAmount > 0.0
    // Settlement date = payment date field: keep Full Settlement / Interest Only Amount in sync.
    androidx.compose.runtime.LaunchedEffect(usePaise, leanAmountPreset, paymentAsOf, totalOutstanding, interestOutstanding) {
        if (!usePaise || leanAmountPreset == null) return@LaunchedEffect
        when (leanAmountPreset) {
            "full" -> if (totalOutstanding > 0.0) {
                amountStr = CurrencyFormatter.wholeRupeesInput(totalOutstanding)
            }
            "interest" -> if (interestOutstanding > 0.0) {
                amountStr = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
            }
        }
    }
    val paisePreview = remember(usePaise, totalAmount, borrower, payments, paymentAsOf) {
        if (usePaise && totalAmount > 0.0) {
            InterestPolicy.previewBorrowerPaymentPaise(
                borrower,
                InterestEngine.rupeesToPaise(totalAmount),
                paymentAsOf,
                payments,
            )
        } else null
    }
    val periodMayNeedReview = remember(interestVal, interestOutstanding, accruedInterest, interestBreakdown.paid, usePaise) {
        if (usePaise) false else {
            val paidAheadAfter = (interestBreakdown.paid + interestVal - accruedInterest).coerceAtLeast(0.0)
            interestVal > 0.0 && (
                interestVal + 0.01 >= interestOutstanding ||
                    paidAheadAfter > 0.01
            )
        }
    }
    val preferredAccount = remember(accountOptions) { preferredMoneyAccount(accountOptions) }
    var selectedAccount by remember(accountOptions) { mutableStateOf(preferredAccount) }
    var accountManuallyPicked by remember(accountOptions) { mutableStateOf(false) }
    LaunchedEffect(totalAmount, accountOptions) {
        if (!accountManuallyPicked && totalAmount > 0.0 && selectedAccount.balance < totalAmount) {
            accountOptions
                .filter { it.balance >= totalAmount }
                .maxByOrNull { it.balance }
                ?.let { selectedAccount = it }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 16.dp).imePadding(),
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {

                Text("Collect Repayment",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("From: ${borrower.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                // -- Outstanding summary --------------------------------------
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Outstanding", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(if (usePaise) "Principal remaining" else "Principal", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.detail(principalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SemanticRed)
                        }
                        if (borrower.rate > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Accrued interest", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(accruedInterest, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Interest collected", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestBreakdown.paid, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Emerald500)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    if (usePaise) "Interest Due" else "Interest (${borrower.rate}% ${InterestEngine.label(borrower.intType, borrower.compoundFrequency)})",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(CurrencyFormatter.detail(interestOutstanding, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SemanticAmber)
                            }
                            if (borrower.interestWaived > 0.0) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Reduced / waived", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "-${CurrencyFormatter.detail(borrower.interestWaived, currencyCode, locale)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Paid ahead", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestBreakdown.paidAhead, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Emerald500)
                            }
                            if (interestBreakdown.paidAhead > 0.01) {
                                Text(
                                    "Interest due is zero until accrued interest catches up. Accrual still continues from the original loan date.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total Due", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(CurrencyFormatter.detail(totalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // -- Auto-suggest buttons -------------------------------------
                // Interest Only: show when interest is outstanding
                if (borrower.rate > 0 && interestOutstanding > 0) {
                    Button(
                        onClick = {
                            if (usePaise) {
                                leanAmountPreset = "interest"
                                amountStr = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
                            } else {
                                interestStr  = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
                                principalStr = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Interest Only - ${CurrencyFormatter.detail(interestOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Full Settlement: show whenever ANY amount is outstanding
                if (totalOutstanding > 0) {
                    Button(
                        onClick = {
                            if (usePaise) {
                                leanAmountPreset = "full"
                                amountStr = CurrencyFormatter.wholeRupeesInput(totalOutstanding)
                            } else {
                                interestStr  = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
                                principalStr = CurrencyFormatter.wholeRupeesInput(principalOutstanding)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Full Settlement - ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // -- Amount entry ---------------------------------------------
                Text(
                    if (usePaise) "Payment amount" else "Payment Breakdown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (usePaise) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            leanAmountPreset = null
                            amountStr = it.filter { c -> c.isDigit() } // whole rupees only
                        },
                        label = { Text("Amount") },
                        placeholder = { Text("0") },
                        prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Split is automatic: interest first, then principal. Settlement uses the payment date above.")
                        },
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = principalStr,
                            onValueChange = { principalStr = it },
                            label = { Text("Principal") },
                            placeholder = { Text("0") },
                            prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SemanticRed,
                                focusedLabelColor  = SemanticRed
                            )
                        )
                        OutlinedTextField(
                            value = interestStr,
                            onValueChange = { interestStr = it },
                            label = { Text("Interest") },
                            placeholder = { Text("0") },
                            prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SemanticAmber,
                                focusedLabelColor  = SemanticAmber
                            )
                        )
                    }
                }

                // -- Total ----------------------------------------------------
                if (totalAmount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Text("Total collecting: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.detail(totalAmount, currencyCode, locale),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = Emerald500)
                    }
                }
                // Lean v1: show Backend-identical allocation preview (no local invent).
                paisePreview?.let { split ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "How this payment lands",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Toward interest", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.towardInterest), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SemanticAmber,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Toward principal", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.towardPrincipal), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = SemanticRed,
                                )
                            }
                            if (split.penaltyPaise > 0L) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Penalty on this account", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.penaltyPaise), currencyCode, locale),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = SemanticAmber,
                                    )
                                }
                            }
                            val remainingAfter = if (split.closesLoan) 0L else
                                (paiseBalances!!.outstanding - split.towardInterest - split.towardPrincipal).coerceAtLeast(0L)
                            if (split.closesLoan) {
                                Text(
                                    "Paid in full",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Emerald500,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Remaining outstanding after", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(remainingAfter), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // -- Destination account --------------------------------------
                // Lean v1 auto-allocates interest→principal; skip legacy period picker.
                if (!usePaise && interestVal > 0.0) {
                    InterestPeriodSelector(
                        selected = interestAllocationType,
                        onSelected = { interestAllocationType = it },
                        currentStartDate = borrower.date,
                        isDebt = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    InterestImpactPreview(
                        allocationType = interestAllocationType,
                        accrued = accruedInterest,
                        dueAfter = (accruedInterest - interestBreakdown.paid - interestVal - borrower.interestWaived).coerceAtLeast(0.0),
                        paidAheadAfter = (interestBreakdown.paid + interestVal - accruedInterest).coerceAtLeast(0.0),
                        currencyCode = currencyCode,
                    )
                    if (periodMayNeedReview) {
                        Spacer(Modifier.height(8.dp))
                        PeriodCompletionNotice()
                    }
                    Spacer(Modifier.height(12.dp))
                }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedAccount.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Deposit into account") },
                        supportingText = {
                            Text("${selectedAccount.type} - Balance: ${CurrencyFormatter.exact(selectedAccount.balance, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accountOptions.forEach { acct ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(when (acct.type.lowercase()) {
                                                "cash" -> Icons.Default.Wallet
                                                "upi"  -> Icons.Default.MonetizationOn
                                                else   -> Icons.Default.AccountBalance
                                            }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(acct.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(CurrencyFormatter.exact(acct.balance, currencyCode, locale),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = {
                                    selectedAccount = acct
                                    accountManuallyPicked = true
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                DatePickerField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Payment Date",
                )
                val dateError = remember(borrower.date, paymentAsOf) { InterestPolicy.paymentDateError(borrower.date, paymentAsOf) }
                var confirmFutureDate by remember { mutableStateOf(false) }
                if (dateError != null) {
                    Text(
                        dateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                    val recordPayment: () -> Unit = recordPayment@{
                            if (submitting) return@recordPayment
                            submitting = true
                            val asOf = DateUtils.parseInput(date).ifBlank { paymentAsOf }
                            val split = if (usePaise) {
                                InterestPolicy.previewBorrowerPaymentPaise(
                                    borrower,
                                    InterestEngine.rupeesToPaise(totalAmount),
                                    asOf,
                                    payments,
                                )
                            } else null
                            val finalPrincipal = split?.let { InterestEngine.paiseToRupees(it.towardPrincipal) } ?: principalVal
                            val finalInterest = split?.let { InterestEngine.paiseToRupees(it.towardInterest) } ?: interestVal
                            val payment = Payment(
                                id        = app.fynlo.logic.Ids.newId(),
                                loanId    = borrower.id,
                                name      = borrower.name,
                                date      = asOf,
                                type      = when {
                                    finalPrincipal > 0 && finalInterest > 0 -> "Both"
                                    finalPrincipal > 0 -> "Principal Only"
                                    finalInterest > 0 -> "Interest Only"
                                    else -> "Both"
                                },
                                amount    = totalAmount,
                                principal = finalPrincipal,
                                interest  = finalInterest,
                                interestPeriodStartDate = if (usePaise) borrower.date else InterestPolicy.periodStartFor(interestAllocationType, borrower.date),
                                interestPeriodEndDate = if (usePaise) asOf else InterestPolicy.periodEndFor(interestAllocationType, borrower.date, asOf),
                                interestAllocationType = if (usePaise) {
                                    if (finalInterest > 0.0) InterestPolicy.CURRENT_PERIOD_INTEREST else InterestPolicy.PRINCIPAL_REPAYMENT
                                } else {
                                    InterestPolicy.allocationFor(finalPrincipal, finalInterest, interestAllocationType)
                                },
                                notes     = split?.let { InterestPolicy.notesWithEngineTags(notes, it) } ?: notes,
                                penaltyPaise = split?.penaltyPaise ?: 0L,
                                roundingPaise = split?.roundingPaise ?: 0L,
                            )
                            onConfirm(payment, selectedAccount.name)
                    }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (InterestPolicy.isFuturePaymentDate(paymentAsOf)) confirmFutureDate = true else recordPayment()
                        },
                        enabled = isValid && !submitting && dateError == null,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500)
                    ) { Text("Record ${CurrencyFormatter.detail(totalAmount, currencyCode, locale)}") }
                }
                if (confirmFutureDate) {
                    FynloConfirmDialog(
                        title = "Future payment date?",
                        message = "This payment is dated after today. Record it anyway?",
                        confirmText = "Record",
                        onConfirm = { confirmFutureDate = false; recordPayment() },
                        onDismiss = { confirmFutureDate = false },
                    )
                }
                // C17 (3.2.42) - both PaymentDialog action buttons gate on
                // totalAmount > 0; surface that as an inline hint when zero.
                DisabledButtonHint(if (isValid) null else "Enter an amount to continue")
            }
        }
    }
}

// --- Pay Debt ---------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayDebtDialog(
    debt: Debt,
    payments: List<DebtPayment> = emptyList(),
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onConfirm: (DebtPayment, String) -> Unit,
    currencyCode: String = "INR",
) {
    val locale = LocalLocale.current.platformLocale
    val usePaise = InterestPolicy.usesPaiseMethod(debt.intType)
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    val paymentAsOf = remember(date) {
        runCatching { DateUtils.parseInput(date) }.getOrDefault("")
            .ifBlank { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    }
    val paiseBalances = remember(debt, payments, usePaise, paymentAsOf) {
        if (usePaise) InterestPolicy.paiseBalancesForDebt(debt, paymentAsOf, payments) else null
    }
    val interestBreakdown = remember(debt, payments) {
        if (payments.isEmpty()) {
            app.fynlo.logic.InterestPolicy.debtBreakdown(debt)
        } else {
            app.fynlo.logic.InterestPolicy.debtBreakdown(debt, payments)
        }
    }
    val accruedInterest = interestBreakdown.accrued
    // v3.3.1: one shared balance function (paise: at the payment date; legacy: today).
    val sharedBalance = remember(debt, payments, usePaise, paymentAsOf) {
        if (usePaise) InterestPolicy.debtBalance(debt, payments, paymentAsOf)
        else InterestPolicy.debtBalance(debt, payments)
    }
    val interestOutstanding = sharedBalance.interestDue
    val principalOutstanding = sharedBalance.principal
    val totalOutstanding     = interestOutstanding + principalOutstanding

    var amountStr by remember { mutableStateOf("") }
    var leanAmountPreset by remember { mutableStateOf<String?>(null) } // "full" | "interest" | null
    var principalStr by remember { mutableStateOf("") }
    var interestStr  by remember { mutableStateOf("") }
    var notes    by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var interestAllocationType by remember { mutableStateOf(InterestPolicy.CURRENT_PERIOD_INTEREST) }

    val accountOptions = if (accounts.isNotEmpty()) accounts
    else listOf(Account(id = "cash", name = "Personal Cash", type = "Cash", balance = 0.0))

    val principalVal = principalStr.toDoubleOrNull() ?: 0.0
    val interestVal  = interestStr.toDoubleOrNull()  ?: 0.0
    val amountVal    = amountStr.toDoubleOrNull() ?: 0.0
    val totalAmount  = if (usePaise) amountVal else principalVal + interestVal
    val isValid      = totalAmount > 0.0
    androidx.compose.runtime.LaunchedEffect(usePaise, leanAmountPreset, paymentAsOf, totalOutstanding, interestOutstanding) {
        if (!usePaise || leanAmountPreset == null) return@LaunchedEffect
        when (leanAmountPreset) {
            "full" -> if (totalOutstanding > 0.0) {
                amountStr = CurrencyFormatter.wholeRupeesInput(totalOutstanding)
            }
            "interest" -> if (interestOutstanding > 0.0) {
                amountStr = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
            }
        }
    }
    val paisePreview = remember(usePaise, totalAmount, debt, payments, paymentAsOf) {
        if (usePaise && totalAmount > 0.0) {
            InterestPolicy.previewDebtPaymentPaise(
                debt,
                InterestEngine.rupeesToPaise(totalAmount),
                paymentAsOf,
                payments,
            )
        } else null
    }
    val periodMayNeedReview = remember(interestVal, interestOutstanding, accruedInterest, interestBreakdown.paid, usePaise) {
        if (usePaise) false else {
            val paidAheadAfter = (interestBreakdown.paid + interestVal - accruedInterest).coerceAtLeast(0.0)
            interestVal > 0.0 && (
                interestVal + 0.01 >= interestOutstanding ||
                    paidAheadAfter > 0.01
            )
        }
    }
    val preferredAccount = remember(accountOptions) { preferredMoneyAccount(accountOptions) }
    var selectedAccount by remember(accountOptions) { mutableStateOf(preferredAccount) }
    var accountManuallyPicked by remember(accountOptions) { mutableStateOf(false) }
    LaunchedEffect(totalAmount, accountOptions) {
        if (!accountManuallyPicked && totalAmount > 0.0 && selectedAccount.balance < totalAmount) {
            accountOptions
                .filter { it.balance >= totalAmount }
                .maxByOrNull { it.balance }
                ?.let { selectedAccount = it }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 16.dp).imePadding(),
            shape    = MaterialTheme.shapes.extraLarge,
            color    = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Pay Debt",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                Text("To: ${debt.name}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))

                // Outstanding summary
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("You Owe", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(if (usePaise) "Principal remaining" else "Principal", style = MaterialTheme.typography.bodySmall)
                            Text(CurrencyFormatter.detail(principalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        if (debt.rate > 0) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Accrued interest", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(accruedInterest, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Interest paid", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestBreakdown.paid, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error)
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(
                                    if (usePaise) "Interest Due" else "Interest (${debt.rate}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(CurrencyFormatter.detail(interestOutstanding, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.error)
                            }
                            if (debt.interestWaived > 0.0) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Reduced / waived", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "-${CurrencyFormatter.detail(debt.interestWaived, currencyCode, locale)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Paid ahead", style = MaterialTheme.typography.bodySmall)
                                Text(CurrencyFormatter.detail(interestBreakdown.paidAhead, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Emerald500)
                            }
                            if (interestBreakdown.paidAhead > 0.01) {
                                Text(
                                    "Interest due is zero until accrued interest catches up. Accrual still continues from the original debt date.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Total Due", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            Text(CurrencyFormatter.detail(totalOutstanding, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Auto-suggest buttons
                if (debt.rate > 0 && interestOutstanding > 0) {
                    Button(onClick = {
                        if (usePaise) {
                            leanAmountPreset = "interest"
                            amountStr = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
                        } else {
                            interestStr = CurrencyFormatter.wholeRupeesInput(interestOutstanding); principalStr = ""
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Interest Only - ${CurrencyFormatter.detail(interestOutstanding, currencyCode, locale)}")
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Full Settlement - always show when any amount is outstanding
                if (totalOutstanding > 0) {
                    Button(onClick = {
                        if (usePaise) {
                            leanAmountPreset = "full"
                            amountStr = CurrencyFormatter.wholeRupeesInput(totalOutstanding)
                        } else {
                            interestStr  = CurrencyFormatter.wholeRupeesInput(interestOutstanding)
                            principalStr = CurrencyFormatter.wholeRupeesInput(principalOutstanding)
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Emerald500.copy(alpha = 0.15f))) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = Emerald500)
                        Spacer(Modifier.width(6.dp))
                        Text("Full Settlement - ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}", color = Emerald500)
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    if (usePaise) "Payment amount" else "Payment Breakdown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (usePaise) {
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = {
                            leanAmountPreset = null
                            amountStr = it.filter { c -> c.isDigit() } // whole rupees only
                        },
                        label = { Text("Amount") },
                        placeholder = { Text("0") },
                        prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text("Split is automatic: interest first, then principal. Settlement uses the payment date above.")
                        },
                    )
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = principalStr, onValueChange = { principalStr = it },
                            label = { Text("Principal") }, placeholder = { Text("0") }, prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f))
                        OutlinedTextField(value = interestStr, onValueChange = { interestStr = it },
                            label = { Text("Interest") }, placeholder = { Text("0") }, prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.error,
                                focusedLabelColor  = MaterialTheme.colorScheme.error))
                    }
                }

                if (totalAmount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        Text("Total paying: ", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(CurrencyFormatter.detail(totalAmount, currencyCode, locale),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                paisePreview?.let { split ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "How this payment lands",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Toward interest", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.towardInterest), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Toward principal", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.towardPrincipal), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                            if (split.penaltyPaise > 0L) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                    Text("Penalty on this account", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        CurrencyFormatter.detail(InterestEngine.paiseToRupees(split.penaltyPaise), currencyCode, locale),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = SemanticAmber,
                                    )
                                }
                            }
                            val remainingAfter = if (split.closesLoan) 0L else
                                (paiseBalances!!.outstanding - split.towardInterest - split.towardPrincipal).coerceAtLeast(0L)
                            if (split.closesLoan) {
                                Text(
                                    "Paid in full",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = Emerald500,
                                )
                            }
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text("Remaining outstanding after", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    CurrencyFormatter.detail(InterestEngine.paiseToRupees(remainingAfter), currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (!usePaise && interestVal > 0.0) {
                    InterestPeriodSelector(
                        selected = interestAllocationType,
                        onSelected = { interestAllocationType = it },
                        currentStartDate = debt.date,
                        isDebt = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    InterestImpactPreview(
                        allocationType = interestAllocationType,
                        accrued = accruedInterest,
                        dueAfter = (accruedInterest - interestBreakdown.paid - interestVal - debt.interestWaived).coerceAtLeast(0.0),
                        paidAheadAfter = (interestBreakdown.paid + interestVal - accruedInterest).coerceAtLeast(0.0),
                        currencyCode = currencyCode,
                    )
                    if (periodMayNeedReview) {
                        Spacer(Modifier.height(8.dp))
                        PeriodCompletionNotice()
                    }
                    Spacer(Modifier.height(12.dp))
                }

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = selectedAccount.name, onValueChange = {}, readOnly = true,
                        label = { Text("Pay from account") },
                        supportingText = {
                            Text("${selectedAccount.type} - Balance: ${CurrencyFormatter.exact(selectedAccount.balance, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accountOptions.forEach { acct ->
                            DropdownMenuItem(
                                text = {
                                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(when (acct.type.lowercase()) {
                                                "cash" -> Icons.Default.Wallet
                                                "upi"  -> Icons.Default.MonetizationOn
                                                else   -> Icons.Default.AccountBalance
                                            }, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(acct.name, fontWeight = FontWeight.Medium)
                                                Text(acct.type, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(CurrencyFormatter.exact(acct.balance, currencyCode, locale),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (acct.balance >= 0) Emerald500 else MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = {
                                    selectedAccount = acct
                                    accountManuallyPicked = true
                                    expanded = false
                                })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                DatePickerField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Payment Date",
                )
                val dateError = remember(debt.date, paymentAsOf) { InterestPolicy.paymentDateError(debt.date, paymentAsOf) }
                var confirmFutureDate by remember { mutableStateOf(false) }
                if (dateError != null) {
                    Text(
                        dateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))

                    val recordPayment: () -> Unit = recordPayment@{
                            if (submitting) return@recordPayment
                            submitting = true
                            val asOf = DateUtils.parseInput(date).ifBlank { paymentAsOf }
                            val split = if (usePaise) {
                                InterestPolicy.previewDebtPaymentPaise(
                                    debt,
                                    InterestEngine.rupeesToPaise(totalAmount),
                                    asOf,
                                    payments,
                                )
                            } else null
                            val finalPrincipal = split?.let { InterestEngine.paiseToRupees(it.towardPrincipal) } ?: principalVal
                            val finalInterest = split?.let { InterestEngine.paiseToRupees(it.towardInterest) } ?: interestVal
                            val payment = DebtPayment(
                                id        = app.fynlo.logic.Ids.newId(),
                                debtId    = debt.id,
                                name      = debt.name,
                                date      = asOf,
                                type      = when {
                                    finalPrincipal > 0 && finalInterest > 0 -> "Both"
                                    finalPrincipal > 0 -> "Principal Only"
                                    finalInterest > 0 -> "Interest Only"
                                    else -> "Both"
                                },
                                amount    = totalAmount,
                                principal = finalPrincipal,
                                interest  = finalInterest,
                                interestPeriodStartDate = if (usePaise) debt.date else InterestPolicy.periodStartFor(interestAllocationType, debt.date),
                                interestPeriodEndDate = if (usePaise) asOf else InterestPolicy.periodEndFor(interestAllocationType, debt.date, asOf),
                                interestAllocationType = if (usePaise) {
                                    if (finalInterest > 0.0) InterestPolicy.CURRENT_PERIOD_INTEREST else InterestPolicy.PRINCIPAL_REPAYMENT
                                } else {
                                    InterestPolicy.allocationFor(finalPrincipal, finalInterest, interestAllocationType)
                                },
                                notes     = split?.let { InterestPolicy.notesWithEngineTags(notes, it) } ?: notes,
                                penaltyPaise = split?.penaltyPaise ?: 0L,
                                roundingPaise = split?.roundingPaise ?: 0L,
                            )
                            onConfirm(payment, selectedAccount.name)
                    }
                Row(Modifier.fillMaxWidth(), Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (InterestPolicy.isFuturePaymentDate(paymentAsOf)) confirmFutureDate = true else recordPayment()
                        },
                        enabled = isValid && !submitting && dateError == null,
                        shape   = RoundedCornerShape(14.dp),
                        colors  = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500)
                    ) { Text("Pay ${CurrencyFormatter.detail(totalAmount, currencyCode, locale)}") }
                }
                if (confirmFutureDate) {
                    FynloConfirmDialog(
                        title = "Future payment date?",
                        message = "This payment is dated after today. Record it anyway?",
                        confirmText = "Record",
                        onConfirm = { confirmFutureDate = false; recordPayment() },
                        onDismiss = { confirmFutureDate = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestPeriodSelector(
    selected: String,
    onSelected: (String) -> Unit,
    currentStartDate: String,
    isDebt: Boolean,
) {
    val options = listOf(
        InterestPolicy.CURRENT_PERIOD_INTEREST to "This loan period",
        InterestPolicy.OLD_PERIOD_INTEREST to "Older interest",
        InterestPolicy.ADVANCE_INTEREST to "Paid in advance",
        InterestPolicy.EXTRA_INTEREST to "Extra note",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Which interest is this?",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Emerald500,
                        selectedLabelColor = Color.White,
                    ),
                )
            }
        }
        val subject = if (isDebt) "debt" else "loan"
        val help = when (selected) {
            InterestPolicy.OLD_PERIOD_INTEREST -> "Keeps this for an older period. It will not reduce interest due from ${DateUtils.formatToDisplay(currentStartDate)}."
            InterestPolicy.ADVANCE_INTEREST -> "Saves this as interest paid in advance for this $subject. Interest due stays zero until time catches up."
            InterestPolicy.EXTRA_INTEREST -> "Saves this as an extra interest note. It will not reduce interest due now."
            else -> "Uses this against interest due from ${DateUtils.formatToDisplay(currentStartDate)}."
        }
        Text(help, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InterestImpactPreview(
    allocationType: String,
    accrued: Double,
    dueAfter: Double,
    paidAheadAfter: Double,
    currencyCode: String,
) {
    val locale = LocalLocale.current.platformLocale
    val affectsCurrentPeriod = allocationType in setOf(
        InterestPolicy.CURRENT_PERIOD_INTEREST,
        InterestPolicy.ADVANCE_INTEREST,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Interest built up", style = MaterialTheme.typography.labelSmall)
                Text(CurrencyFormatter.detail(accrued, currencyCode, locale), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
            }
            if (affectsCurrentPeriod) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Interest due after save", style = MaterialTheme.typography.labelSmall)
                    Text(CurrencyFormatter.detail(dueAfter, currencyCode, locale), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                }
                if (paidAheadAfter > 0.01) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("Paid ahead after save", style = MaterialTheme.typography.labelSmall)
                        Text(CurrencyFormatter.detail(paidAheadAfter, currencyCode, locale), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = Emerald500)
                    }
                }
            } else {
                Text(
                    "This amount stays in payment history and will not change interest due now.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeriodCompletionNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = app.fynlo.ui.theme.SemanticAmber.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Check this interest payment",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = app.fynlo.ui.theme.SemanticAmber,
            )
            Text(
                "If it fully covers interest due, pays ahead, or belongs to an older period, choose the right option here. Loan and debt dates are never changed silently.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiveInterestDialog(
    title: String,
    subtitle: String,
    maxWaivable: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
    currencyCode: String = "INR",
) {
    val locale = LocalLocale.current.platformLocale
    var amountText by remember(maxWaivable) { mutableStateOf(CurrencyFormatter.wholeRupeesInput(maxWaivable)) }
    var reason by remember { mutableStateOf("Grace period waived") }
    var submitting by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    // Whole-rupee entry: allow up to the rounded-up rupee; confirm clamps to the exact max.
    val isValid = amount > 0.0 && amount <= kotlin.math.ceil(maxWaivable)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).padding(vertical = 16.dp).imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Emerald500.copy(alpha = 0.10f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Available to waive", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            CurrencyFormatter.detail(maxWaivable, currencyCode, locale),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Emerald500,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Waiver amount") },
                    prefix = { Text(CurrencyUtils.symbolFor(currencyCode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = amount > maxWaivable,
                )
                if (amount > maxWaivable) {
                    Text(
                        "Amount cannot exceed unpaid interest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(22.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.End, Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (submitting || !isValid) return@Button
                            submitting = true
                            onConfirm(amount.coerceAtMost(maxWaivable), reason)
                        },
                        enabled = isValid && !submitting,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Waive Interest")
                    }
                }
                DisabledButtonHint(
                    when {
                        maxWaivable <= 0.0 -> "No unpaid interest to waive"
                        amount <= 0.0 -> "Enter an amount to continue"
                        amount > maxWaivable -> "Reduce the amount to unpaid interest"
                        else -> null
                    }
                )
            }
        }
    }
}
