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


internal data class ProofGap(
    val title: String,
    val detail: String,
    val suggestion: String,
    val accent: Color,
)

internal fun buildProofGaps(
    attachments: List<app.fynlo.data.model.ProofAttachment>,
    transactions: List<app.fynlo.data.model.Transaction>,
    borrowers: List<app.fynlo.data.model.Borrower>,
    debts: List<app.fynlo.data.model.Debt>,
    investments: List<app.fynlo.data.model.Investment>,
): List<ProofGap> {
    val proofKeys = attachments
        .map { it.ownerType.lowercase() to it.ownerId }
        .toSet()
    fun hasProof(ownerType: String, ownerId: String): Boolean =
        ownerType.lowercase() to ownerId in proofKeys

    val loanGaps = borrowers
        .filter { it.amount >= 50_000.0 && !hasProof("loan", it.id) && !hasProof("borrower", it.id) }
        .map {
            ProofGap(
                title = "Loan proof missing",
                detail = "${it.name} - ${CurrencyFormatter.detail(it.amount)} lent on ${it.date}",
                suggestion = "Open the loan statement and attach the receipt, bank screenshot, or agreement.",
                accent = Amber,
            )
        }
    val debtGaps = debts
        .filter { it.amount >= 50_000.0 && !hasProof("debt", it.id) }
        .map {
            ProofGap(
                title = "Debt proof missing",
                detail = "${it.name} - ${CurrencyFormatter.detail(it.amount)} received on ${it.date}",
                suggestion = "Open the debt statement and attach proof showing where the money came from.",
                accent = Amber,
            )
        }
    val investmentGaps = investments
        .filter { it.invested >= 50_000.0 && !hasProof("investment", it.id) }
        .map {
            ProofGap(
                title = "Investment proof missing",
                detail = "${it.name} - ${CurrencyFormatter.detail(it.invested)} invested on ${it.date}",
                suggestion = "Open the investment and attach the purchase proof or account statement.",
                accent = Amber,
            )
        }
    val largeTransactionGaps = transactions
        .filter {
            kotlin.math.abs(it.amount) >= 50_000.0 &&
                !it.isGeneratedJournalEntry() &&
                !hasProof("transaction", it.id)
        }
        .sortedWith(compareByDescending<app.fynlo.data.model.Transaction> { it.date }.thenByDescending { it.updatedAt })
        .take(8)
        .map {
            val account = when (it.type.lowercase()) {
                "income" -> it.toAcct
                "expense" -> it.fromAcct
                else -> listOf(it.fromAcct, it.toAcct).filter { account -> account.isNotBlank() }.joinToString(" -> ")
            }.ifBlank { "account not selected" }
            ProofGap(
                title = "Large transaction to review",
                detail = "${it.category} - ${CurrencyFormatter.detail(it.amount)} on ${it.date} - $account",
                suggestion = "Keep supporting proof. If this belongs to a loan, debt, or investment, attach proof on that detail screen.",
                accent = Carbon500,
            )
        }

    return (loanGaps + debtGaps + investmentGaps + largeTransactionGaps)
        .sortedBy { it.title }
}

@Composable
internal fun ProofVaultDialog(
    attachments: List<app.fynlo.data.model.ProofAttachment>,
    proofGaps: List<ProofGap>,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = "Proof vault",
        subtitle = "${attachments.size} saved links - ${proofGaps.size} review gaps",
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Green.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Green.copy(alpha = 0.16f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Proof holding",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "Use this to review receipts, screenshots, agreements, and high-value entries that deserve proof before closing a month. Older gaps are review prompts, not balance errors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProofVaultMetric("Saved", attachments.size.toString(), Green, Modifier.weight(1f))
                ProofVaultMetric("Review", proofGaps.size.toString(), if (proofGaps.isEmpty()) Green else Amber, Modifier.weight(1f))
            }

            if (proofGaps.isNotEmpty()) {
                LedgerDialogSectionTitle("Needs proof review")
                proofGaps.take(12).forEach { gap ->
                    LedgerInfoCard(
                        title = gap.title,
                        detail = gap.detail,
                        accent = gap.accent,
                        suggestion = gap.suggestion,
                    )
                }
                if (proofGaps.size > 12) {
                    Text(
                        "+${proofGaps.size - 12} older proof gaps kept for review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "No large proof gaps found. Existing proof links are still visible below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LedgerDialogSectionTitle("Saved proof links")
            if (attachments.isEmpty()) {
                Text(
                    "No proof links saved yet. Add proof from loan, debt, and investment detail screens.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                attachments
                    .sortedByDescending { it.updatedAt }
                    .take(20)
                    .forEach { proof ->
                        ProofAttachmentRow(proof)
                    }
                if (attachments.size > 20) {
                    Text(
                        "+${attachments.size - 20} older proof links",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
internal fun ProofVaultMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ProofAttachmentRow(proof: app.fynlo.data.model.ProofAttachment) {
    LedgerInfoCard(
        title = proof.displayName.ifBlank { "Saved proof" },
        detail = buildString {
            append(proof.ownerType.replaceFirstChar { it.uppercase() })
            append(" proof")
            if (proof.mimeType.isNotBlank()) append(" - ${proof.mimeType}")
            if (proof.note.isNotBlank()) append(" - ${proof.note}")
        },
        accent = Green,
        suggestion = "Open the related ${proof.ownerType.lowercase()} detail screen to review or replace this proof.",
    )
}

