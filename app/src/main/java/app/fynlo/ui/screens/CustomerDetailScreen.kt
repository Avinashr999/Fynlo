package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.net.toUri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Payment
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.components.AddLendingDialog
import app.fynlo.ui.components.CollectPaymentDialog
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.components.FormDialog
import app.fynlo.ui.components.ProofAttachmentSection
import app.fynlo.ui.components.WaiveInterestDialog
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    borrowerId: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
        val haptic = LocalHapticFeedback.current
val borrowers by viewModel.borrowers.collectAsState()
    val allPayments by viewModel.payments.collectAsState()
    val proofAttachments by viewModel.proofAttachments.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val locale = LocalLocale.current.platformLocale

    // C11 (3.2.40) - user's Date Format pref for the loan-statement PDF.
    val dateFormat by app.fynlo.data.UserPreferences.dateFormat(androidx.compose.ui.platform.LocalContext.current)
        .collectAsState(initial = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN)

    val borrower = borrowers.find { it.id == borrowerId }

    if (borrower == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Customer not found")
        }
        return
    }

    val loanPayments = allPayments
        .filter { it.loanId == borrowerId }
        .sortedByDescending { it.date }
    val loanProofs = proofAttachments.filter { it.ownerType == "loan" && it.ownerId == borrowerId }

    val interestBreakdown = app.fynlo.logic.InterestPolicy.borrowerBreakdown(borrower, loanPayments)
    val interest = interestBreakdown.accrued
    val interestOutstanding = interestBreakdown.due
    val advanceInterest = interestBreakdown.paidAhead
    val totalOutstanding = (borrower.amount - borrower.paidPrincipal).coerceAtLeast(0.0) + interestOutstanding

    val context = androidx.compose.ui.platform.LocalContext.current

    var showEditDialog    by remember { mutableStateOf(false) }
    var showCollectDialog by remember { mutableStateOf(false) }
    var showWaiveInterestDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // C12 Stage 3 (3.2.28) - new action surfaces lifted from LendingCard.
    var showReminderPicker by remember { mutableStateOf(false) }
    var showDefaultConfirm by remember { mutableStateOf(false) }
    var showWriteOffConfirm by remember { mutableStateOf(false) }
    var showEffectiveReturn by remember { mutableStateOf(false) }
    var showPaymentHistory by remember { mutableStateOf(false) }
    var deleteInProgress by remember(borrower.id) { mutableStateOf(false) }
    var defaultInProgress by remember(borrower.id) { mutableStateOf(false) }
    var writeOffInProgress by remember(borrower.id) { mutableStateOf(false) }

    if (showEditDialog) {
        AddLendingDialog(
            viewModel = viewModel,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated, source ->
                viewModel.updateBorrowerWithSource(updated, source)
                viewModel.showFeedback("Loan updated")
                showEditDialog = false
            },
            initialBorrower = borrower
        )
    }

    if (showCollectDialog) {
        CollectPaymentDialog(
            borrower = borrower,
            payments = loanPayments,
            accounts = accounts,
            onDismiss = { showCollectDialog = false },
            onConfirm = { payment, dest ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.collectLoanPayment(payment, dest)
                viewModel.showFeedback("Payment collected")
                showCollectDialog = false
            }
        )
    }

    if (showWaiveInterestDialog) {
        WaiveInterestDialog(
            title = "Waive interest",
            subtitle = "For: ${borrower.name}",
            maxWaivable = interestOutstanding,
            onDismiss = { showWaiveInterestDialog = false },
            onConfirm = { amount, reason ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.waiveBorrowerInterest(borrower, amount, reason)
                viewModel.showFeedback("Interest waived")
                showWaiveInterestDialog = false
            },
            currencyCode = currencyCode,
        )
    }

    if (showDeleteConfirm) {
        FynloConfirmDialog(
            title = "Delete borrower?",
            message = "This will delete ${borrower.name} and all payment records linked to this loan. The account balance will be reversed.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                if (!deleteInProgress) {
                    deleteInProgress = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.deleteBorrower(borrower)
                    viewModel.showFeedback("Loan deleted")
                    showDeleteConfirm = false
                    onNavigateBack()
                }
            },
        )
    }

    // C12 Stage 3 - Send Reminder picker. Consolidates the previously-separate
    // WhatsApp and SMS list-row icons into a single channel-picker dialog per
    // audit sectionC12 #8. The smart message builder (overdue-aware, includes per-day
    // interest accrual) was lifted from the old LendingCard.
    if (showReminderPicker) {
        val phone = borrower.phone.trim()
        val intlPhone = remember(phone) {
            val p = phone.replace(" ", "").replace("-", "")
            when {
                p.startsWith("+") -> p
                p.startsWith("91") && p.length == 12 -> "+$p"
                p.length == 10 -> "+91$p"
                else -> p
            }
        }
        val today    = remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
        val daysOverdue: Long = if (borrower.due.isNotBlank() && borrower.due < today) {
            InterestEngine.daysBetween(borrower.due, today)
        } else 0L
        val daysSince = InterestEngine.daysBetween(borrower.date, today)
        val waMsg = buildString {
            appendLine("Hi ${borrower.name},")
            appendLine()
            if (daysOverdue > 0) {
                appendLine("This is a reminder that your loan repayment is *${app.fynlo.logic.pluralize(daysOverdue, "day")} overdue*.")
                appendLine()
                appendLine("*Loan Summary:*")
                appendLine("* Principal: ${CurrencyFormatter.detail(borrower.amount, currencyCode, locale)}")
                if (borrower.rate > 0) appendLine("* Interest accrued (${borrower.rate}% ${InterestEngine.label(borrower.intType)}): ${CurrencyFormatter.detail(interest, currencyCode, locale)}")
                if (borrower.paid > 0)  appendLine("* Amount paid so far: ${CurrencyFormatter.detail(borrower.paid, currencyCode, locale)}")
                appendLine("* *Total outstanding: ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}*")
                appendLine()
                append("Please arrange payment at your earliest. ")
                if (borrower.rate > 0) {
                    val dailyRate = borrower.amount * borrower.rate / 36500.0
                    append("Interest is adding ~${CurrencyFormatter.detail(dailyRate, currencyCode, locale)}/day.")
                }
            } else {
                val dueInfo = if (borrower.due.isNotBlank()) {
                    val daysLeft = InterestEngine.daysBetween(today, borrower.due)
                    " (due in ${app.fynlo.logic.pluralize(daysLeft, "day")})"
                } else ""
                appendLine("This is a friendly reminder about your outstanding loan$dueInfo.")
                appendLine()
                appendLine("*Loan Summary:*")
                appendLine("* Principal: ${CurrencyFormatter.detail(borrower.amount, currencyCode, locale)}")
                if (borrower.rate > 0) appendLine("* Interest so far ($daysSince days @ ${borrower.rate}%): ${CurrencyFormatter.detail(interest, currencyCode, locale)}")
                if (borrower.paid > 0)  appendLine("* Paid: ${CurrencyFormatter.detail(borrower.paid, currencyCode, locale)}")
                appendLine("* *Outstanding: ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}*")
                appendLine()
                append("Kindly arrange repayment at your convenience. Thank you!")
            }
            appendLine(); append("- Fynlo Ledger")
        }.trimEnd()
        val smsMsg = buildString {
            if (daysOverdue > 0) append("Hi ${borrower.name}, your loan of ${CurrencyFormatter.detail(borrower.amount, currencyCode, locale)} is $daysOverdue days overdue. ")
            else append("Hi ${borrower.name}, loan reminder: ")
            append("Outstanding: ${CurrencyFormatter.detail(totalOutstanding, currencyCode, locale)}")
            if (borrower.rate > 0) append(" (incl. interest)")
            append(". Please repay soon. -Fynlo Ledger")
        }

        FormDialog(
            title = "Send Reminder",
            subtitle = if (phone.isBlank()) "Add a phone number before sending reminders." else "Send to $phone",
            onDismiss = { showReminderPicker = false },
        ) {
            if (phone.isBlank()) {
                Text(
                    "No phone number on file. Tap Edit to add one before sending reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "WhatsApp uses the full message. SMS is trimmed to the essentials.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showReminderPicker = false }) { Text("Close") }
                if (phone.isNotBlank()) {
                    TextButton(onClick = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_SENDTO,
                                "smsto:$intlPhone".toUri()
                            ).apply { putExtra("sms_body", smsMsg) }
                        )
                        showReminderPicker = false
                    }) {
                        Icon(Icons.Default.Sms, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMS")
                    }
                    TextButton(onClick = {
                        val uri = "https://wa.me/$intlPhone?text=${android.net.Uri.encode(waMsg)}".toUri()
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) {
                            context.startActivity(android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, waMsg)
                                }, "Send Reminder"
                            ))
                        }
                        showReminderPicker = false
                    }) {
                        Icon(Icons.Default.Call, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }
            }
        }
    }

    // C12 Stage 3 - Mark NPA / Restore confirmation (lifted from LendingScreen).
    if (showDefaultConfirm) {
        val isCurrentlyDefaulted = borrower.status == "Defaulted"
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showDefaultConfirm = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, TemplateBorder),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        if (isCurrentlyDefaulted) "Restore to Performing?" else "Mark as Defaulted?",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (isCurrentlyDefaulted)
                            "This will mark ${borrower.name} as active again and unfreeze interest accrual from today."
                        else
                            "Interest will be frozen at today's value. ${borrower.name} will be marked NPA.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showDefaultConfirm = false }) { Text("Cancel") }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (!defaultInProgress) {
                                    defaultInProgress = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isCurrentlyDefaulted) viewModel.restoreBorrowerToActive(borrower)
                                    else viewModel.markBorrowerDefaulted(borrower)
                                    showDefaultConfirm = false
                                }
                            },
                            enabled = !defaultInProgress,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCurrentlyDefaulted) Emerald500 else app.fynlo.ui.theme.SemanticAmber
                            )
                        ) { Text(if (isCurrentlyDefaulted) "Restore" else "Mark NPA") }
                    }
                }
            }
        }
    }

    // C12 Stage 3 - Write Off confirmation (lifted from LendingScreen).
    if (showWriteOffConfirm) {
        FynloConfirmDialog(
            title = "Write off bad debt?",
            message = "This creates a Bad Debt expense and removes ${borrower.name} from receivables. Use this only when the amount is no longer recoverable.",
            confirmText = "Write off",
            destructive = true,
            onDismiss = { showWriteOffConfirm = false },
            onConfirm = {
                if (!writeOffInProgress) {
                    writeOffInProgress = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.writeOffBorrower(borrower)
                    showWriteOffConfirm = false
                    onNavigateBack()
                }
            },
        )
    }

    Scaffold(
        topBar = {
            LedgerDetailTopBar(
                title = borrower.name,
                subtitle = "Loan statement",
                onNavigateBack = onNavigateBack,
            ) {
                LedgerTopBarActionButton(
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit",
                    onClick = { showEditDialog = true },
                )
                LedgerTopBarActionButton(
                    icon = Icons.Default.PictureAsPdf,
                    contentDescription = "Export PDF",
                    onClick = {
                        // C21 Stage 1 - standardized filename + identity row
                        // (project + signed-in email on the PDF cover).
                        val file = app.fynlo.logic.ExportUtility.exportCacheFile(
                            context,
                            app.fynlo.logic.ExportUtility.filename("LoanStatement", borrower.name, "pdf")
                        )
                        file.outputStream().use { os ->
                            app.fynlo.logic.ExportUtility.generateLoanStatementPDF(
                                os, borrower, emptyList(), interest, totalOutstanding,
                                currencyCode = currencyCode,
                                projectName  = currentProject?.name ?: "Personal",
                                userEmail    = app.fynlo.data.AuthManager().userEmail,
                                dateFormat   = dateFormat,
                            )
                        }
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.provider", file
                        )
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }, "Share Loan Statement"
                        ))
                    },
                )
                LedgerTopBarActionButton(
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete",
                    onClick = { showDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Total Receivable", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // C16 (3.2.41) - Lent-side outstanding is a receivable
                    // (asset), so positive outstanding renders green
                    // (semantic_income), not red. When fully repaid
                    // (zero) the number is neutral onSurface rather than
                    // green - green would imply "extra income"; the
                    // borrower simply finished paying.
                    Text(
                        CurrencyFormatter.hero(totalOutstanding, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (totalOutstanding > 0) Emerald500
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DetailItem("Principal Outstanding", CurrencyFormatter.detail((borrower.amount - borrower.paidPrincipal).coerceAtLeast(0.0), currencyCode, locale))
                        DetailItem("Interest Due", CurrencyFormatter.interest(interestOutstanding, currencyCode, locale))
                        DetailItem("Total Receivable", CurrencyFormatter.detail(totalOutstanding, currencyCode, locale))
                    }
                    if (borrower.interestWaived > 0.0) {
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Interest before reduction: ${CurrencyFormatter.interest(interest, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Reduced / waived: -${CurrencyFormatter.interest(borrower.interestWaived, currencyCode, locale)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (advanceInterest > 0.01) {
                        Spacer(Modifier.height(10.dp))
                        InterestPaidAheadNotice(
                            title = "Interest paid ahead",
                            body = "Interest due is zero for now because extra interest was already collected. Interest will continue adding from the loan date.",
                            advance = CurrencyFormatter.interest(advanceInterest, currencyCode, locale),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = Emerald500,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Disbursed from",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                borrower.sourceAccount.ifBlank { "Unknown account" },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailItem(
                            "Loan date",
                            DateUtils.formatToDisplay(borrower.date),
                            modifier = Modifier.weight(1f),
                        )
                        DetailItem(
                            "Due date",
                            borrower.due.takeIf { it.isNotBlank() }?.let { DateUtils.formatToDisplay(it) } ?: "No due date",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailItem(
                            "Interest rate",
                            interestRateLabel(borrower.rate),
                            modifier = Modifier.weight(1f),
                        )
                        DetailItem(
                            "Interest method",
                            interestMethodLabel(borrower.rate, borrower.intType),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (totalOutstanding > 0) {
                item {
                    DetailActionButton(
                        text = "Collect Payment",
                        icon = Icons.Default.Payments,
                        onClick = { showCollectDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        emphasized = true,
                    )
                }
            }

            // C12 Stage 3 - Send Reminder is a primary-tier action for the
            // borrower workflow (chasing repayment is the most common action
            // after Collect). Placed right under Collect; opens a channel
            // picker so the user chooses WhatsApp vs SMS once instead of
            // both icons cluttering the row.
            item {
                DetailActionButton(
                    text = "Send Reminder",
                    icon = Icons.Default.NotificationsActive,
                    onClick = { showReminderPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (borrower.rate > 0.0 && interestOutstanding > 0.0) {
                item {
                    DetailActionButton(
                        text = "Waive interest",
                        icon = Icons.Default.MoneyOff,
                        onClick = { showWaiveInterestDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // C12 Stage 3 - NPA + Write Off were per-card actions in the old
            // LendingCard. They live here now as secondary buttons under the
            // primary collect/reminder pair. Write Off is only meaningful while
            // there's outstanding balance to charge off.
            item {
                val isDefaulted = borrower.status == "Defaulted"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailActionButton(
                        text = if (isDefaulted) "Restore Active" else "Mark NPA",
                        icon = if (isDefaulted) Icons.Default.Block else Icons.Default.Warning,
                        onClick = { showDefaultConfirm = true },
                        modifier = Modifier.weight(1f),
                        warning = !isDefaulted,
                    )
                    if (totalOutstanding > 0) {
                        DetailActionButton(
                            text = "Write Off",
                            icon = Icons.Default.MoneyOff,
                            onClick = { showWriteOffConfirm = true },
                            modifier = Modifier.weight(1f),
                            destructive = true,
                        )
                    }
                }
            }

            item {
                LoanExpandableSection(
                    title = "Payment History",
                    subtitle = if (loanPayments.isEmpty()) "No payments collected yet"
                               else app.fynlo.logic.pluralize(loanPayments.size, "payment"),
                    expanded = showPaymentHistory,
                    onToggle = { showPaymentHistory = !showPaymentHistory },
                ) {
                    if (loanPayments.isEmpty()) {
                        Text(
                            "No payments collected yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            loanPayments.forEach { payment ->
                                PaymentItem(payment, currencyCode, locale)
                            }
                        }
                    }
                }
            }

            // 3.2.83 - XIRR (effective annualised return) on this loan.
            // Cashflows from lender's perspective:
            //   - principal disbursed on `loanDate` as a NEGATIVE outflow
            //   - each Payment received as a POSITIVE inflow on its date
            //   - imputed current `totalOutstanding` at today's date as a
            //     POSITIVE "if I called the loan now" cashflow, so XIRR
            //     stays meaningful for ongoing loans (not yet fully paid).
            //     Excel / Sheets `XIRR` does the same when modelling
            //     mark-to-market on an open position.
            // Hidden when there are no payments OR the math degenerates
            // (single cashflow, no inflows, etc.). Format renders "-" then.
            if (loanPayments.isNotEmpty()) {
                item {
                    val xirr = remember(loanPayments, borrower, totalOutstanding) {
                        val flows = mutableListOf<app.fynlo.logic.XirrCalculator.Cashflow>()
                        flows += app.fynlo.logic.XirrCalculator.Cashflow(
                            amount = -borrower.amount,
                            date   = borrower.date,
                        )
                        loanPayments.forEach { p ->
                            flows += app.fynlo.logic.XirrCalculator.Cashflow(p.amount, p.date)
                        }
                        if (totalOutstanding > 0.01) {
                            flows += app.fynlo.logic.XirrCalculator.Cashflow(
                                amount = totalOutstanding,
                                date   = java.time.LocalDate.now()
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            )
                        }
                        app.fynlo.logic.XirrCalculator.calc(flows)
                    }
                    if (!xirr.isNaN()) {
                        val xirrColor = if (xirr >= 0) Emerald500 else SemanticRed
                        LoanExpandableSection(
                            title = "Effective return",
                            subtitle = "Annualised return across repayments (XIRR)",
                            expanded = showEffectiveReturn,
                            onToggle = { showEffectiveReturn = !showEffectiveReturn },
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = xirrColor.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "XIRR",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        app.fynlo.logic.XirrCalculator.format(xirr),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = xirrColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                ProofAttachmentSection(
                    title = "Loan proof",
                    attachments = loanProofs,
                    onAddProof = { name, type, uri ->
                        viewModel.addProofAttachment("loan", borrower.id, name, type, uri)
                    },
                    onDeleteProof = viewModel::deleteProofAttachment,
                )
            }

            if (borrower.notes.isNotBlank()) {
                item {
                    Text(
                        "Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(borrower.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanExpandableSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun PaymentItem(payment: Payment, currencyCode: String, locale: Locale) {
    val interest = app.fynlo.logic.InterestPolicy.paymentInterestAmount(payment)
    val title = paymentHistoryTitle(
        principal = payment.principal,
        interest = interest,
        allocationType = payment.interestAllocationType,
        isDebt = false,
    ).ifBlank { payment.type.ifBlank { "Repayment" } }
    val details = paymentHistoryDetails(
        principal = payment.principal,
        interest = interest,
        allocationType = payment.interestAllocationType,
        periodStart = payment.interestPeriodStartDate,
        periodEnd = payment.interestPeriodEndDate,
        notes = payment.notes,
        currencyCode = currencyCode,
        locale = locale,
        isDebt = false,
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Emerald500.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Payments,
                contentDescription = null,
                tint = Emerald500,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                DateUtils.formatToDisplay(payment.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            details.forEach { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "+${CurrencyFormatter.detail(payment.amount, currencyCode, locale)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = Emerald500
            )
        )
    }
}

fun paymentHistoryTitle(
    principal: Double,
    interest: Double,
    allocationType: String,
    isDebt: Boolean,
): String {
    if (principal > 0.01 && interest > 0.01) return "Mixed payment"
    if (principal > 0.01) return if (isDebt) "Principal paid" else "Principal collected"
    if (interest <= 0.01) return ""
    return when (allocationType) {
        app.fynlo.logic.InterestPolicy.OLD_PERIOD_INTEREST -> "Older interest"
        app.fynlo.logic.InterestPolicy.ADVANCE_INTEREST -> "Interest paid ahead"
        app.fynlo.logic.InterestPolicy.EXTRA_INTEREST -> "Extra interest"
        app.fynlo.logic.InterestPolicy.UNKNOWN_REVIEW, "" -> "Interest needs review"
        else -> if (isDebt) "Interest paid" else "Interest collected"
    }
}

fun paymentHistoryDetails(
    principal: Double,
    interest: Double,
    allocationType: String,
    periodStart: String,
    periodEnd: String,
    notes: String,
    currencyCode: String,
    locale: Locale,
    isDebt: Boolean,
): List<String> = buildList {
    val parts = mutableListOf<String>()
    if (principal > 0.01) {
        parts += "${if (isDebt) "Principal paid" else "Principal collected"} ${CurrencyFormatter.detail(principal, currencyCode, locale)}"
    }
    if (interest > 0.01) {
        parts += "${if (isDebt) "Interest paid" else "Interest collected"} ${CurrencyFormatter.interest(interest, currencyCode, locale)}"
    }
    if (parts.isNotEmpty()) add(parts.joinToString(" * "))

    if (interest > 0.01 && periodStart.isNotBlank()) {
        val end = periodEnd.takeIf { it.isNotBlank() } ?: "ongoing"
        add("Covers ${DateUtils.formatToDisplay(periodStart)} to ${if (end == "ongoing") end else DateUtils.formatToDisplay(end)}")
    }

    when (allocationType) {
        app.fynlo.logic.InterestPolicy.OLD_PERIOD_INTEREST ->
            add("Saved for an older period. It is not reducing interest due now.")
        app.fynlo.logic.InterestPolicy.EXTRA_INTEREST ->
            add("Saved as extra interest. It is not reducing interest due now.")
        app.fynlo.logic.InterestPolicy.UNKNOWN_REVIEW, "" ->
            if (interest > 0.01) add("Choose where this interest belongs before closing the month.")
    }

    if (notes.isNotBlank()) add(notes)
}

@Composable
fun DetailItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
    }
}

fun interestRateLabel(rate: Double): String {
    if (rate <= 0.0) return "No interest"
    val clean = rate.toBigDecimal().stripTrailingZeros().toPlainString()
    return "$clean% p.a."
}

fun interestMethodLabel(rate: Double, method: String): String =
    if (rate <= 0.0) "None" else InterestEngine.label(method)
@Composable
fun InterestPaidAheadNotice(
    title: String,
    body: String,
    advance: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    advance,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Emerald500,
                )
            }
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
