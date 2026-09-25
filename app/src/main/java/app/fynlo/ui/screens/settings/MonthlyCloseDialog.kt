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
internal fun MonthlyCloseDialog(
    month: String,
    isClosed: Boolean,
    transactions: List<app.fynlo.data.model.Transaction>,
    currencyCode: String,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val income = remember(transactions) {
        transactions.filter { it.type.equals("Income", ignoreCase = true) && !it.isGeneratedJournalEntry() }
            .sumOf { it.amount }
    }
    val expense = remember(transactions) {
        transactions.filter { it.type.equals("Expense", ignoreCase = true) && !it.isGeneratedJournalEntry() }
            .sumOf { it.amount }
    }
    val transfers = remember(transactions) { transactions.count { it.type.equals("Transfer", ignoreCase = true) } }
    FynloConfirmDialog(
        title = if (isClosed) "Reopen $month-" else "Close $month-",
        message = if (isClosed) {
            "This unlocks the month so corrections can be made. Close it again after checking Book check."
        } else {
            "This locks the month. Money actions dated in this month will be blocked until you reopen it."
        },
        confirmText = if (isClosed) "Reopen Month" else "Close Month",
        onDismiss = onDismiss,
        onConfirm = if (isClosed) onReopen else onClose,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Month review", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        CloseMetricRow("Entries", transactions.size.toString())
                        CloseMetricRow("Income", CurrencyFormatter.detail(income, currencyCode))
                        CloseMetricRow("Expense", CurrencyFormatter.detail(expense, currencyCode))
                        CloseMetricRow("Transfers", transfers.toString())
                    }
                }
            }
        },
    )
}

@Composable
internal fun CloseMetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

