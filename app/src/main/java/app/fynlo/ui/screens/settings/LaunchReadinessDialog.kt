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


internal data class LaunchReadinessItem(
    val title: String,
    val detail: String,
    val ready: Boolean,
    val accent: Color,
)

internal fun buildLaunchReadinessItems(
    ledgerReport: app.fynlo.logic.LedgerAccountabilityReport,
    proofGapCount: Int,
    syncStatus: app.fynlo.data.SyncStatus,
    previousMonthClosed: Boolean,
): List<LaunchReadinessItem> {
    val syncReady = syncStatus is app.fynlo.data.SyncStatus.Synced
    val bookReady = ledgerReport.criticalCount == 0 && ledgerReport.warningCount == 0
    val noCritical = ledgerReport.criticalCount == 0
    return listOf(
        LaunchReadinessItem(
            "Personal ledger treatment",
            "Income, expense, transfer, loan, debt, investment, and waiver rows are explained separately so transfers do not look like profit.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Automatic monthly close",
            if (previousMonthClosed) "Previous month is already locked." else "Previous month can be closed from this assistant after Book check is clean.",
            ready = previousMonthClosed || noCritical,
            accent = if (previousMonthClosed || noCritical) Green else Amber,
        ),
        LaunchReadinessItem(
            "Smart alerts",
            "Dashboard brings due recurring entries, budget limit alerts, proof gaps, and Book check problems into one review surface.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Clean Play Store onboarding",
            "First-run screens explain separate books, lending, debt, investments, cloud backup, exports, and backups without developer wording.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Reports 2.0",
            "Reports hub has range filters, compact metric previews, PDF export, P&L, net worth, money flow, monthly summary, debt payoff, and EMI routes.",
            ready = bookReady,
            accent = if (bookReady) Green else Amber,
        ),
        LaunchReadinessItem(
            "Import automation",
            "CSV import supports file picking, column mapping, preview, and account selection before bulk adding rows.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Backup confidence",
            if (syncReady) "Cloud backup is synced and manual encrypted backup/export tools are available." else "Cloud backup is not fully synced yet. Wait for sync before release smoke.",
            ready = syncReady,
            accent = if (syncReady) Green else Amber,
        ),
        LaunchReadinessItem(
            "Security polish",
            "PIN/biometric lock, encrypted backup option, privacy/legal copy, and safe reset warnings are available.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Business mode",
            "Projects keep separate books, and business-style investment/project categories are already supported.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Smart explanations",
            "Book check explains why each issue matters and what to do next without depending on any AI service.",
            ready = true,
            accent = Green,
        ),
        LaunchReadinessItem(
            "Release quality system",
            "Use compile, unit tests, phone smoke, Book check, Proof vault, backup/export, and Play internal testing before promotion.",
            ready = ledgerReport.criticalCount == 0 && proofGapCount == 0,
            accent = if (ledgerReport.criticalCount == 0 && proofGapCount == 0) Green else Amber,
        ),
    )
}

@Composable
internal fun LaunchReadinessDialog(
    ledgerReport: app.fynlo.logic.LedgerAccountabilityReport,
    proofGapCount: Int,
    syncStatus: app.fynlo.data.SyncStatus,
    previousMonth: String,
    previousMonthClosed: Boolean,
    onClosePreviousMonth: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember(ledgerReport, proofGapCount, syncStatus, previousMonthClosed) {
        buildLaunchReadinessItems(
            ledgerReport = ledgerReport,
            proofGapCount = proofGapCount,
            syncStatus = syncStatus,
            previousMonthClosed = previousMonthClosed,
        )
    }
    val reviewCount = items.count { !it.ready }
    FormDialog(
        title = "Launch readiness",
        subtitle = if (reviewCount == 0) "All checks look ready" else "$reviewCount checks need review",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (reviewCount == 0) Green.copy(alpha = 0.08f) else Amber.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, if (reviewCount == 0) Green.copy(alpha = 0.16f) else Amber.copy(alpha = 0.18f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (reviewCount == 0) "Release confidence is high" else "Release needs a short review",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "This checks the practical launch areas: ledger trust, close status, backup, reports, imports, security, business mode, and support readiness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LedgerDialogSectionTitle("Monthly close assistant")
            LedgerInfoCard(
                title = previousMonth,
                detail = if (previousMonthClosed) {
                    "Previous month is already closed."
                } else {
                    "Close this month after reviewing Book check. Closing prevents accidental changes to older records."
                },
                accent = if (previousMonthClosed) Green else Amber,
                suggestion = if (previousMonthClosed) {
                    "No action needed."
                } else if (ledgerReport.criticalCount == 0) {
                    "Tap Close previous month if your totals look correct."
                } else {
                    "Fix serious Book check items before closing."
                },
            )
            Button(
                onClick = onClosePreviousMonth,
                enabled = !previousMonthClosed && ledgerReport.criticalCount == 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (previousMonthClosed) "Already closed" else "Close previous month")
            }

            LedgerDialogSectionTitle("Release checks")
            items.forEach { item ->
                LaunchReadinessRow(item)
            }

            TemplatePrimaryButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun LaunchReadinessRow(item: LaunchReadinessItem) {
    LedgerInfoCard(
        title = "${if (item.ready) "Ready" else "Review"} - ${item.title}",
        detail = item.detail,
        accent = item.accent,
        suggestion = if (item.ready) "Keep this in the release smoke checklist." else "Review this before promoting the next build.",
    )
}

