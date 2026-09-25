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
internal fun SyncConflictDialog(
    conflicts: List<app.fynlo.data.model.SyncConflict>,
    onResolve: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    FormDialog(
        title = "Cloud backup review",
        subtitle = if (conflicts.isEmpty()) "No open conflicts" else "${conflicts.size} item${if (conflicts.size == 1) "" else "s"} need a choice",
        onDismiss = onDismiss,
    ) {
        if (conflicts.isEmpty()) {
            Text(
                "This phone and cloud backup are aligned right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                conflicts.forEach { conflict ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${friendlyConflictCollection(conflict.collection)} changed in two places",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                "This phone and cloud backup both changed this item. Choose the copy that should be kept.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (conflict.fieldSummary.isNotBlank()) {
                                LedgerInfoCard(
                                    title = "What changed",
                                    detail = conflict.fieldSummary,
                                    accent = Amber,
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ConflictSnapshotCard(
                                    title = "Phone copy",
                                    value = conflict.localJson,
                                    modifier = Modifier.weight(1f),
                                )
                                ConflictSnapshotCard(
                                    title = "Cloud",
                                    value = conflict.remoteJson,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                OutlinedButton(
                                    onClick = { onResolve(conflict.id, "KeepPhone") },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Keep phone")
                                }
                                OutlinedButton(
                                    onClick = { onResolve(conflict.id, "KeepCloud") },
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text("Keep cloud")
                                }
                                TextButton(
                                    onClick = { onResolve(conflict.id, "Reviewed") },
                                ) {
                                    Text("Mark reviewed")
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Done") }
    }
}

@Composable
internal fun ConflictSnapshotCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val preview = remember(value) { readableConflictPreview(value) }
    Surface(
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                preview,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun readableConflictPreview(value: String): String {
    if (value.isBlank()) return "No saved preview"
    return runCatching {
        val fields = Json.parseToJsonElement(value).jsonObject
        friendlyConflictFields(fields)
    }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: value.take(220)
}

internal fun friendlyConflictCollection(collection: String): String {
    return when (collection.lowercase()) {
        "accounts" -> "Account"
        "transactions" -> "Transaction"
        "borrowers", "lending" -> "Loan"
        "debts" -> "Debt"
        "investments" -> "Investment"
        "projects" -> "Book"
        else -> collection.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

internal fun friendlyConflictFields(fields: JsonObject): String {
    val priority = listOf(
        "name", "title", "description", "category", "type", "date", "loanDate", "borrowedDate", "dueDate",
        "amount", "principal", "balance", "paid", "paidPrincipal", "paidInterest", "rate",
        "account", "fromAcct", "fromAcctId", "toAcct", "toAcctId", "fundingSource", "currentValue", "invested",
    )
    return priority
        .mapNotNull { key ->
            val label = friendlyConflictLabel(key) ?: return@mapNotNull null
            val raw = (fields[key] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull
                ?: fields[key]?.toString()?.trim('"')
                ?: return@mapNotNull null
            raw.takeIf { it.isNotBlank() && it != "null" }?.let { "$label: $it" }
        }
        .take(5)
        .joinToString("\n")
}

internal fun friendlyConflictLabel(key: String): String? {
    return when (key) {
        "name", "title" -> "Name"
        "description" -> "Note"
        "category" -> "Category"
        "type" -> "Type"
        "date", "loanDate", "borrowedDate" -> "Date"
        "dueDate" -> "Due date"
        "amount" -> "Amount"
        "principal" -> "Principal"
        "balance" -> "Balance"
        "paid" -> "Paid"
        "paidPrincipal" -> "Principal paid"
        "paidInterest" -> "Interest paid"
        "rate" -> "Interest rate"
        "account" -> "Account"
        "fromAcct", "fromAcctId" -> "From account"
        "toAcct", "toAcctId" -> "To account"
        "fundingSource" -> "Funded from"
        "currentValue" -> "Current value"
        "invested" -> "Invested"
        else -> null
    }
}

