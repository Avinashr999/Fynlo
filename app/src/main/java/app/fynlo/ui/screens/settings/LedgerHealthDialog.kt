package app.fynlo.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.components.FormDialog
import app.fynlo.ui.theme.ThemeController
import app.fynlo.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Green = Emerald500
private val Blue  = SemanticBlue
private val Red   = SemanticRed
private val Amber = SemanticAmber


@Composable
internal fun LedgerHealthDialog(
    report: app.fynlo.logic.LedgerAccountabilityReport,
    repairInFlight: Boolean,
    repairSummary: String?,
    showAdvancedTools: Boolean,
    onOpenIssue: (app.fynlo.logic.LedgerIssue) -> Unit,
    onRunSafeRepair: () -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = "Book check",
        subtitle = "${report.headline} - Score ${report.score}/100",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LedgerHealthSummary(report)
            if (report.criticalCount > 0 || report.issues.any { canSafeRepairHelp(it) }) {
                LedgerSafeRepairCard(
                    inFlight = repairInFlight,
                    summary = repairSummary,
                    onRunSafeRepair = onRunSafeRepair,
                )
            }
            if (showAdvancedTools) {
                RepairCoverageCard(report)
                ReconciliationGuideCard(report)
            }
            if (report.issues.isNotEmpty()) {
                LedgerIssueGroup(
                    title = "Needs action",
                    issues = report.issues.filter { it.severity == app.fynlo.logic.LedgerIssueSeverity.CRITICAL },
                    onOpenIssue = onOpenIssue,
                )
                LedgerIssueGroup(
                    title = "Review only",
                    issues = report.issues.filter { it.severity == app.fynlo.logic.LedgerIssueSeverity.WARNING },
                    onOpenIssue = onOpenIssue,
                )
                val infoIssues = report.issues.filter { it.severity == app.fynlo.logic.LedgerIssueSeverity.INFO }
                if (showAdvancedTools) {
                    LedgerIssueGroup(
                        title = "Already okay / old records",
                        issues = infoIssues,
                        onOpenIssue = onOpenIssue,
                        limit = 4,
                    )
                } else if (infoIssues.isNotEmpty()) {
                    Text(
                        "${infoIssues.size} old-record notes are available in advanced review.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "No money record problems found in the current project.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (report.duplicates.isNotEmpty()) {
                LedgerDialogSectionTitle("Possible duplicates")
                report.duplicates.take(5).forEach { duplicate ->
                    LedgerInfoCard(
                        title = duplicate.title,
                        detail = duplicate.detail,
                        accent = Amber,
                    )
                }
            }
            if (showAdvancedTools && report.trails.isNotEmpty()) {
                LedgerDialogSectionTitle("Money paths")
                report.trails.take(8).forEach { trail ->
                    LedgerInfoCard(
                        title = trail.title,
                        detail = trail.route,
                        accent = Green,
                    )
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Done") }
        }
    }
}

@Composable
internal fun LedgerIssueGroup(
    title: String,
    issues: List<app.fynlo.logic.LedgerIssue>,
    onOpenIssue: (app.fynlo.logic.LedgerIssue) -> Unit,
    limit: Int = 6,
) {
    if (issues.isEmpty()) return
    LedgerDialogSectionTitle(title)
    issues.take(limit).forEach { issue ->
        LedgerIssueRow(
            issue = issue,
            onOpen = if (issue.recordType.isNotBlank() && issue.recordId.isNotBlank()) {
                { onOpenIssue(issue) }
            } else null,
        )
    }
    if (issues.size > limit) {
        Text(
            "+${issues.size - limit} more in this section",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun app.fynlo.delegate.BookRepairResult.toSafeRepairSummary(
    formatDelta: (Double) -> String,
): String {
    errorMessage?.let { return "Could not finish: $it" }
    val parts = buildList {
        if (deletedResidue > 0) add("$deletedResidue deleted row cleanup")
        if (debtFundedTransferTraces > 0) add("$debtFundedTransferTraces investment money-path fixes")
        if (debtFundedJournalRefs > 0) add("$debtFundedJournalRefs investment trail links")
        if (debtReceiptMismatches > 0) add("$debtReceiptMismatches debt receipt amounts")
        if (transactionAccountIds > 0) add("$transactionAccountIds account links")
        if (accountBalanceDrift > 0) add("$accountBalanceDrift account balance repairs")
    }
    val delta = recalcDelta
    if (parts.isEmpty() && delta?.isNoOp != false) {
        return "No fix needed. Saved balances already match your money records."
    }
    val repairText = if (parts.isEmpty()) "No safe fixes needed" else parts.joinToString(", ")
    val balanceText = if (delta == null) {
        "account total check not run"
    } else {
        "net worth ${formatDelta(delta.netWorthChange)}, cash ${formatDelta(delta.cashChange)}, investments ${formatDelta(delta.investmentsChange)}"
    }
    return "$repairText. $balanceText."
}

@Composable
internal fun RepairCoverageCard(report: app.fynlo.logic.LedgerAccountabilityReport) {
    val autoHelpCount = remember(report.issues) { report.issues.count { canSafeRepairHelp(it) } }
    val manualCount = (report.issues.size - autoHelpCount).coerceAtLeast(0)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "What safe fix can do",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "Safe fix can rebuild known account links and correct totals when the evidence is already saved in your book.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "It will not guess accounts, change dates, delete possible duplicates, or decide which person is correct.",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = Amber,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RepairCoveragePill("Can fix", autoHelpCount.toString(), Green, Modifier.weight(1f))
                RepairCoveragePill("Review", manualCount.toString(), Amber, Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun RepairCoveragePill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = color)
        }
    }
}

internal fun canSafeRepairHelp(issue: app.fynlo.logic.LedgerIssue): Boolean {
    val text = "${issue.title} ${issue.detail}".lowercase()
    return "receipt amount mismatch" in text ||
        "debt-funded investment" in text ||
        "balance drift" in text ||
        "account id" in text ||
        "ledger trace duplicate" in text
}

@Composable
internal fun ReconciliationGuideCard(report: app.fynlo.logic.LedgerAccountabilityReport) {
    val nextStep = when {
        report.criticalCount > 0 -> "Fix safe issues first, then review every critical item before exporting."
        report.warningCount > 0 -> "Warnings usually mean older records are missing a clear money path. Review them before month close."
        else -> "No ledger problems found. You can close the month after checking totals."
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review path", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            ReconcileStep("1", "Fix safe issues", "Only rebuilds links and totals from records already in your book.")
            ReconcileStep("2", "Check remaining warnings", "Open the related account, loan, debt, investment, or transaction history.")
            ReconcileStep("3", "Close the month", "Lock the reviewed month so old entries cannot accidentally change.")
            Text(nextStep, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Green)
        }
    }
}

@Composable
internal fun ReconcileStep(number: String, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = Green.copy(alpha = 0.12f)) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Green,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LedgerSafeRepairCard(
    inFlight: Boolean,
    summary: String?,
    onRunSafeRepair: () -> Unit,
) {
    Surface(
        color = Green.copy(alpha = 0.08f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.16f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Safe fix",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "Checks saved money records and fixes only clear account-link or total problems.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Button(
                onClick = onRunSafeRepair,
                enabled = !inFlight,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (inFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking...")
                } else {
                    Icon(Icons.Default.Build, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fix safe issues")
                }
            }
        }
    }
}

@Composable
internal fun LedgerHealthSummary(report: app.fynlo.logic.LedgerAccountabilityReport) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LedgerSummaryMetric("Needs fixing", report.criticalCount.toString(), Red)
                LedgerSummaryMetric("Review", report.warningCount.toString(), Amber)
                LedgerSummaryMetric("Linked", report.linkedRecords.toString(), Green)
            }
            Text(
                "Backup: ${report.syncSummary}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Possible duplicates: ${report.duplicateCount}  -  Account links to review: ${report.missingTraceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LedgerSummaryMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LedgerDialogSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun LedgerIssueRow(
    issue: app.fynlo.logic.LedgerIssue,
    onOpen: (() -> Unit)?= null,
) {
    val color = when (issue.severity) {
        app.fynlo.logic.LedgerIssueSeverity.CRITICAL -> Red
        app.fynlo.logic.LedgerIssueSeverity.WARNING -> Amber
        app.fynlo.logic.LedgerIssueSeverity.INFO -> Blue
    }
    LedgerInfoCard(
        title = userFacingLedgerIssueTitle(issue),
        detail = userFacingLedgerIssueDetail(issue),
        accent = color,
        onClick = onOpen,
    )
}

internal fun userFacingLedgerIssueTitle(issue: app.fynlo.logic.LedgerIssue): String {
    val title = issue.title.lowercase()
    val detail = issue.detail.lowercase()
    return when {
        "investment value" in title || ("investment" in title && "value" in title && "missing" in title) -> "Investment value looks missing"
        "interest payment" in title -> "Interest payment needs review"
        "duplicate" in title -> "Possible duplicate record"
        "amount mismatch" in title -> "Amount needs review"
        "investment" in title && ("trace" in title || "path" in title || "link" in title) -> "Investment money path missing"
        "debt" in title && ("receipt" in title || "trace" in title || "path" in title || "link" in title) -> "Debt deposit path missing"
        "loan" in title && ("trace" in title || "path" in title || "link" in title) -> "Loan money path missing"
        ("source" in title || "destination" in title || "account" in title) && ("not found" in title || "missing" in title) -> "Account needs review"
        "trace" in title || "ledger" in title || "link" in title -> "Money path needs review"
        "old" in detail || "older" in detail || "legacy" in detail -> "Older record needs review"
        else -> issue.title
    }
}

internal fun userFacingLedgerIssueDetail(issue: app.fynlo.logic.LedgerIssue): String {
    val cleaned = issue.detail
        .replace("ledger trace", "money path", ignoreCase = true)
        .replace("trace", "money path", ignoreCase = true)
        .replace("ledger", "book", ignoreCase = true)
        .replace("legacy/imported data", "older saved data", ignoreCase = true)
        .replace("linked-row", "saved record", ignoreCase = true)
        .replace("source account id", "source account", ignoreCase = true)
        .replace("destination account id", "destination account", ignoreCase = true)
    return when {
        "interest payment" in issue.title.lowercase() ->
            cleaned
                .substringBefore(". Choose whether")
                .substringBefore(". Open this")
                .let { "$it. Review where this interest belongs." }
        else -> cleaned
    }
}
@Composable
internal fun LedgerInfoCard(
    title: String,
    detail: String,
    accent: Color,
    explanation: String?= null,
    suggestion: String?= null,
    onClick: (() -> Unit)?= null,
) {
    Surface(
        modifier = if (onClick != null) Modifier.fillMaxWidth().clickable(onClick = onClick) else Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(9.dp).padding(top = 6.dp).background(accent, CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!explanation.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Why it matters: $explanation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!suggestion.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Try this: $suggestion",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                    )
                }
                if (onClick != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accent.copy(alpha = 0.10f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                "Open related record",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = accent,
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun ledgerIssueExplanation(issue: app.fynlo.logic.LedgerIssue): String {
    val title = issue.title.lowercase()
    val detail = issue.detail.lowercase()
    return when {
        "interest payment" in title || "interest start date" in title || "interest period" in title || "assign it as previous" in detail ->
            "The payment is saved, but it needs one simple choice so current interest due stays clear."
        "duplicate" in title || "duplicate" in detail ->
            "Two similar money rows can double-count cash, expenses, income, or repayments."
        "invalid" in title || "amount" in title && "mismatch" !in title ->
            "A money row with a bad amount cannot represent a real account movement."
        "transfer" in title && "same account" in detail ->
            "A transfer must reduce one account and add another. Same-account transfers should not change the ledger."
        "transfer" in title || "route" in title ->
            "Transfers need both sides so net worth stays unchanged and account histories stay clear."
        "source" in title || "destination" in title || "account" in title ->
            "Without a clear account, the app cannot prove where the money came from or went."
        "payment total" in title || "unpaid total" in detail ->
            "The statement total and payment rows disagree, so the remaining loan or debt balance may be wrong."
        "receipt amount mismatch" in title || "receipt amount" in detail ->
            "The debt principal and deposited transaction disagree, so debt and account cash can drift apart."
        "receipt link" in title || "debt received" in detail ->
            "The debt exists, but the app cannot show which account received the money."
        "investment" in title && "funding" in title ->
            "The holding exists, but the app cannot prove which account or debt funded it."
        "investment" in title && "debt" in title ->
            "Debt-funded investments need a clear link so they do not incorrectly change account cash."
        "orphan" in title ->
            "A payment without its parent record cannot be trusted in borrower or debt totals."
        "older" in title || "old" in detail ->
            "Older open records may need review so due status, interest, and reports stay meaningful."
        else ->
            "This item affects account clarity. Reviewing it makes balances and reports easier to trust."
    }
}

internal fun ledgerIssueSuggestion(issue: app.fynlo.logic.LedgerIssue): String {
    val title = issue.title.lowercase()
    val detail = issue.detail.lowercase()
    return when {
        "interest payment" in title || "interest start date" in title || "interest period" in title || "assign it as previous" in detail ->
            "Open it and choose older interest, this period, paid ahead, or extra note."
        "duplicate" in title || "duplicate" in detail ->
            "Open History, compare the two entries, and delete only the extra copy."
        "funding" in title || "investment" in title ->
            "Open the investment, confirm the source account, then save it again."
        "receipt link" in title || "debt received" in detail ->
            "Open the debt record and confirm where the borrowed money was deposited."
        "payment total" in title || "unpaid total" in detail ->
            "Open the loan or debt payments and review the latest payment split."
        "source" in title || "account" in detail ->
            "Open the original record and choose the correct account from the account picker."
        else ->
            "Open the original loan, debt, investment, or transaction before changing balances."
    }
}

