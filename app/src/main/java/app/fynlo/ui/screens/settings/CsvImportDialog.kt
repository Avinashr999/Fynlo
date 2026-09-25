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
 * C22 (3.2.66) - password dialog for encrypted-backup export/import.
 *
 * - [BackupPasswordMode.SET]: export flow. Requires a second "confirm
 *   password" field to defend against typos (no recovery if the user
 *   exports with a misremembered password). Minimum length 8.
 * - [BackupPasswordMode.ENTER]: restore flow. Single field; the magic
 *   header is what told us the file is encrypted, so we just need the
 *   password to decrypt. [errorMessage] surfaces "wrong password" from
 *   a previous failed attempt without dismissing the dialog.
 */
/**
 * C22 (3.2.67) - column-mapping dialog for CSV import.
 *
 * Surfaces the parsed CSV headers (first row) as the option set for four
 * column dropdowns: Date, Description, Amount, Category (optional). The
 * user also picks a target Account; all mapped rows land there so the
 * orphan-account regression from 3.2.59 can't recur.
 *
 * Live preview of the first 3 mapped rows updates as the user changes
 * mappings - gives them confidence the columns are right before they
 * commit. Skipped-row count surfaces in the button label.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CsvImportDialog(
    rows: List<List<String>>,
    accounts: List<String>,
    existingTransactions: List<app.fynlo.data.model.Transaction> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (app.fynlo.logic.BankStatementImport.ColumnMap, String) -> Unit,
    initialSummary: String?,
) {
    val header = rows.firstOrNull().orEmpty()
    val dataRowCount = (rows.size - 1).coerceAtLeast(0)

    // Default heuristic picks: scan headers for the obvious names; fall
    // back to index 0/1/2 if no match (works for unlabelled exports).
    fun findCol(vararg keywords: String): Int {
        val i = header.indexOfFirst { h -> keywords.any { k -> h.contains(k, ignoreCase = true) } }
        return if (i >= 0) i else -1
    }
    var dateCol     by remember { mutableIntStateOf(findCol("date", "txn date", "value date").let { if (it < 0) 0 else it }) }
    var descCol     by remember { mutableIntStateOf(findCol("desc", "narration", "particulars", "memo").let { if (it < 0) 1.coerceAtMost(header.lastIndex.coerceAtLeast(0)) else it }) }
    var amountCol   by remember { mutableIntStateOf(findCol("amount", "amt", "debit", "credit").let { if (it < 0) 2.coerceAtMost(header.lastIndex.coerceAtLeast(0)) else it }) }
    // -1 sentinel = "None" (no category column).
    var categoryCol by remember { mutableIntStateOf(findCol("category", "cat")) }
    var targetAccount by remember { mutableStateOf(accounts.firstOrNull().orEmpty()) }

    val mapping = app.fynlo.logic.BankStatementImport.ColumnMap(
        dateCol = dateCol,
        descriptionCol = descCol,
        amountCol = amountCol,
        categoryCol = if (categoryCol < 0) null else categoryCol,
    )
    val preview = remember(rows, mapping, targetAccount, existingTransactions) {
        if (rows.size > 1 && targetAccount.isNotBlank()) {
            app.fynlo.logic.BankStatementImport.mapRows(rows.take(4), mapping, targetAccount, existingTransactions)
        } else emptyList()
    }
    val previewOk = preview.filterIsInstance<app.fynlo.logic.BankStatementImport.RowResult.Ok>()
    val previewSkipped = preview.size - previewOk.size

    app.fynlo.ui.components.FormDialog(
        title = "Map CSV columns",
        onDismiss = onDismiss,
    ) {
        if (initialSummary != null) {
            Text(initialSummary, style = MaterialTheme.typography.bodySmall,
                color = if (initialSummary.startsWith("Imported")) Emerald500
                        else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "$dataRowCount data rows detected. Pick which column holds each piece of information.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        // Header preview chips so the user sees their actual column names.
        if (header.isNotEmpty()) {
            app.fynlo.ui.components.FormSectionLabel("Detected headers")
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                header.forEachIndexed { i, h ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${i + 1}. ${h.take(18)}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        CsvColumnPicker("Date column",        header, dateCol,     allowNone = false) { dateCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Description column", header, descCol,     allowNone = false) { descCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Amount column",      header, amountCol,   allowNone = false) { amountCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Category column (optional)", header, categoryCol, allowNone = true) { categoryCol = it }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Import into account")
        Spacer(Modifier.height(6.dp))
        var acctExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = acctExpanded, onExpandedChange = { acctExpanded = !acctExpanded }) {
            OutlinedTextField(
                value = targetAccount,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = acctExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = acctExpanded, onDismissRequest = { acctExpanded = false }) {
                accounts.forEach { acct ->
                    DropdownMenuItem(text = { Text(acct) },
                        onClick = { targetAccount = acct; acctExpanded = false })
                }
            }
        }

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            app.fynlo.ui.components.FormSectionLabel("Preview (first ${preview.size} rows)")
            Spacer(Modifier.height(6.dp))
            preview.forEach { r ->
                when (r) {
                    is app.fynlo.logic.BankStatementImport.RowResult.Ok -> {
                        val t = r.transaction
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (r.isDuplicate) "Duplicate" else "Ready"}  -  ${t.date}  -  ${t.category}  -  ${"%.2f".format(t.amount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r.isDuplicate) SemanticAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is app.fynlo.logic.BankStatementImport.RowResult.Skip -> {
                        Text(
                            "Skipped row ${r.rowIndex}: ${r.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (previewSkipped > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fix the date/amount column picks if too many rows skip.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        val canConfirm = dataRowCount > 0 && targetAccount.isNotBlank() && previewOk.isNotEmpty()
        Button(
            onClick = { onConfirm(mapping, targetAccount) },
            enabled = canConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text(
                "Import $dataRowCount rows into $targetAccount",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (!canConfirm) {
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    targetAccount.isBlank() -> "Pick a target account."
                    previewOk.isEmpty()     -> "Preview rows all skip - check your column picks."
                    else                    -> "No data rows to import."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun CsvColumnPicker(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    allowNone: Boolean,
    onPick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selectedIndex < 0          -> "None"
        selectedIndex < headers.size -> "${selectedIndex + 1}. ${headers[selectedIndex]}"
        else                       -> "Column ${selectedIndex + 1}"
    }
    Column {
        app.fynlo.ui.components.FormSectionLabel(label)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowNone) {
                    DropdownMenuItem(text = { Text("None") },
                        onClick = { onPick(-1); expanded = false })
                }
                headers.forEachIndexed { i, h ->
                    DropdownMenuItem(
                        text = { Text("${i + 1}. $h") },
                        onClick = { onPick(i); expanded = false }
                    )
                }
            }
        }
    }
}

