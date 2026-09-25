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



/**
 * 3.2.72 - diagnostic dialog showing every account-balance mutation.
 *
 * Entries are reactive (StateFlow from BalanceAuditLog.observe) so the
 * list updates without re-opening the dialog if a sync fires while it's
 * visible. Most useful as: open the dialog, force-stop the app, relaunch,
 * watch what SYNC_PULL entries appear at the top - that's the "what
 * mutated on launch" trace.
 */
@Composable
internal fun BalanceAuditLogDialog(
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries by app.fynlo.logic.BalanceAuditLog.observe(context).collectAsState(initial = emptyList())
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
    }

    app.fynlo.ui.components.FormDialog(
        title = "Balance change log",
        onDismiss = onDismiss,
    ) {
        Text(
            if (entries.isEmpty()) "No balance changes recorded yet. Once you add, edit, sync, or recurring transactions fire, they'll show up here newest-first."
            else "${entries.size} balance mutation${if (entries.size == 1) "" else "s"} recorded (newest first, capped at 200). " +
                 "Look for SYNC_PULL entries on relaunch - those are Firestore overwriting your local balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries, key = { it.timestamp.toString() + it.account + it.source }) { entry ->
                    AuditEntryRow(entry, dateFmt)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Clear log") }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            ) { Text("Close") }
        }
    }
}

@Composable
internal fun AuditEntryRow(
    entry: app.fynlo.logic.BalanceAuditLog.Entry,
    dateFmt: java.time.format.DateTimeFormatter,
) {
    // Tint the row by source so SYNC_PULL stands out - that's the one
    // the diagnostic exists to flag.
    val tintColor = when (entry.source) {
        "SYNC_PULL"          -> SemanticAmber
        "RECURRING_WORKER"   -> Blue
        "MANUAL_TXN"         -> Emerald500
        "DELETE_TXN"         -> MaterialTheme.colorScheme.error
        "QUICK_EDIT_BALANCE" -> SemanticAmber
        else                 -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when {
        entry.delta > 0  -> "+"
        entry.delta < 0  -> "Rs"
        else             -> ""
    }
    val deltaText = "$sign${"%.2f".format(kotlin.math.abs(entry.delta))}"
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tintColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "${entry.source}  -  ${entry.account}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tintColor,
                )
                Text(
                    deltaText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (entry.delta < 0) MaterialTheme.colorScheme.error else Emerald500,
                )
            }
            Text(
                "${dateFmt.format(java.time.Instant.ofEpochMilli(entry.timestamp))} - ${entry.note}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

