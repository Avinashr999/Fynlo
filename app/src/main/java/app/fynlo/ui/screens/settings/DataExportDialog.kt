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


internal enum class DataExportFormat(val label: String, val ext: String) {
    PDF("PDF", "pdf"),
    CSV("CSV", "csv"),
}

internal enum class DataExportScope(
    val id: String,
    val label: String,
    val fileToken: String,
    val subtitle: String,
) {
    WHOLE("whole", "Whole Data", "Whole_Data", "All readable sections"),
    TRANSACTIONS("transactions", "Transactions", "Transactions", "History rows only"),
    ACCOUNTS("accounts", "Accounts", "Accounts", "Account balances"),
    LENDING("lending", "Lending", "Lending", "Loans you gave"),
    DEBTS("debts", "Debts", "Debts", "Money you owe"),
    INVESTMENTS("investments", "Investments", "Investments", "Investment records"),
    PEOPLE("people", "People", "People", "Contact book"),
    BUDGETS("budgets", "Budgets", "Budgets", "Budget limits"),
    GOALS("goals", "Goals", "Goals", "Savings goals"),
}

@Composable
internal fun DataExportDialog(
    selectedScope: DataExportScope,
    selectedFormat: DataExportFormat,
    onScopeChange: (DataExportScope) -> Unit,
    onFormatChange: (DataExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    app.fynlo.ui.components.FormDialog(
        title = "Export Data",
        onDismiss = onDismiss,
    ) {
        app.fynlo.ui.components.FormSectionLabel("Format")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DataExportFormat.entries.forEach { format ->
                FilterChip(
                    selected = selectedFormat == format,
                    onClick = { onFormatChange(format) },
                    label = { Text(format.label) },
                    leadingIcon = if (selectedFormat == format) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        app.fynlo.ui.components.FormSectionLabel("Section")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DataExportScope.entries.forEach { scope ->
                Surface(
                    onClick = { onScopeChange(scope) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedScope == scope) Emerald500.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        0.8.dp,
                        if (selectedScope == scope) Emerald500 else TemplateBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(
                            selected = selectedScope == scope,
                            onClick = { onScopeChange(scope) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                scope.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                scope.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${selectedScope.label} will be exported as ${selectedFormat.label}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The next screen lets you choose where to save the file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Export ${selectedFormat.label}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

