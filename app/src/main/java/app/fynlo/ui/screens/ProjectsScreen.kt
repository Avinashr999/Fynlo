package app.fynlo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Project
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.PremiumScreenHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@Composable
fun ProjectsScreen(viewModel: FinanceViewModel, onNavigateToUpgrade: () -> Unit = {}) {
    val haptic = LocalHapticFeedback.current
    val isPro by app.fynlo.billing.BillingManager.isPro.collectAsState()
    val projects       by viewModel.projects.collectAsState()
    val currentPid     by viewModel.currentProjectId.collectAsState()
    var showAddDialog  by remember { mutableStateOf(false) }
    var deleteTarget   by remember { mutableStateOf<Project?>(null) }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { project ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.createProject(project)
                viewModel.switchProject(project.id)
                viewModel.showFeedback("Project created")
                showAddDialog = false
            }
        )
    }

    // C22 (3.2.56) — confirm-before-duplicate. Duplicating is cheap (one
    // row) but creates a visible row in the user's list — surface a quick
    // confirm so accidental long-presses on the overflow menu don't litter.
    var duplicateTarget by remember { mutableStateOf<Project?>(null) }
    duplicateTarget?.let { proj ->
        AlertDialog(
            onDismissRequest = { duplicateTarget = null },
            title = { Text("Duplicate project?") },
            text  = { Text("Create a copy of \"${proj.name}\"? Only the project shell (name, icon, color, currency, description) is copied — transactions / budgets / goals stay with the original.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.createProject(
                        proj.copy(
                            id = app.fynlo.logic.Ids.newId(),
                            name = "${proj.name} (copy)",
                            createdAt = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                            updatedAt = 0L,
                        )
                    )
                    viewModel.showFeedback("Project duplicated")
                    duplicateTarget = null
                }) { Text("Duplicate") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { proj ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete project?") },
            text  = { Text("All data tagged to \"${proj.name}\" will remain but won't be visible in any project. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.deleteProject(proj)
                    viewModel.showFeedback("Project deleted")
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Projects", "Separate books for every money world")
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(
                "Your books",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isPro || projects.isEmpty()) showAddDialog = true else onNavigateToUpgrade()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Emerald500.copy(alpha = 0.12f),
                    contentColor = Emerald500,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Project")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(projects, key = { _, it -> it.id }) { _, project ->
                ProjectCard(
                    project    = project,
                    isActive   = project.id == currentPid,
                    onSelect   = { viewModel.switchProject(project.id) },
                    onDuplicate = { duplicateTarget = project },
                    onDelete   = {
                        if (project.id != "personal") deleteTarget = project
                    }
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(
    project: Project,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
        val haptic = LocalHapticFeedback.current
        val cardColor by animateColorAsState(
            targetValue = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
            label = "projectCardColor",
        )
        Surface(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(18.dp),
            color = cardColor,
            tonalElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
        ) {
        Row(
            modifier          = Modifier.fillMaxWidth().clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot — now renders the user-picked icon (C22 3.2.56).
            Surface(
                modifier = Modifier.size(42.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = runCatching { Color(android.graphics.Color.parseColor(project.color)) }
                              .getOrDefault(MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = app.fynlo.ui.components.ProjectIcons.iconFor(project.icon),
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = project.name,
                    style      = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                // C22 (3.2.56) — surface description above the currency line
                // if set. Empty description stays hidden so legacy projects
                // look identical to pre-3.2.56.
                if (project.description.isNotBlank()) {
                    Text(
                        text  = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text  = project.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isActive) {
                // C16 (3.2.41) — was Icons.Default.CheckCircle in primary
                // green; the audit flagged the green-check as reading like
                // "income confirmation" instead of a selection indicator.
                // RadioButtonChecked reads unambiguously as a selection
                // state regardless of colour.
                Icon(
                    Icons.Filled.RadioButtonChecked,
                    contentDescription = "Active project",
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
            }

            // C22 (3.2.56) — duplicate action. Available on every project
            // including "personal" — copying personal creates a new project
            // shell the user can rename to e.g. "Personal 2024 archive".
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDuplicate()
            }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Duplicate project",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Cannot delete the default personal project
            if (project.id != "personal") {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete project",
                        tint               = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (Project) -> Unit
) {
    var name         by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var currency      by remember { mutableStateOf("INR") }
    var currExpanded  by remember { mutableStateOf(false) }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val colors = listOf("#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899")
    var selectedColor by remember { mutableStateOf(colors.first()) }
    // C22 (3.2.56) — icon picker. Default "business" matches the pre-3.2.56
    // hardcoded Business icon so an unchanged tap-Create renders identically.
    var iconKey by remember { mutableStateOf("business") }

    // C22 dialog universalization (3.2.54) — migrated to canonical FormDialog.
    app.fynlo.ui.components.FormDialog(
        title = "New Project",
        onDismiss = onDismiss,
    ) {
        app.fynlo.ui.components.FormSectionLabel("Project name")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = name,
            onValueChange = { name = it },
            placeholder   = { Text("e.g. Personal") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Description (optional)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value         = description,
            onValueChange = { description = it },
            placeholder   = { Text("e.g. Side-business expenses") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Icon")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.fynlo.ui.components.ProjectIcons.all.forEach { opt ->
                val selected = iconKey == opt.key
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape    = RoundedCornerShape(12.dp),
                    color    = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    onClick  = { iconKey = opt.key },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            opt.icon,
                            contentDescription = opt.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Currency")
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = currExpanded, onExpandedChange = { currExpanded = !currExpanded }) {
            OutlinedTextField(
                value       = "${app.fynlo.logic.CurrencyUtils.symbolFor(currency)} $currency",
                onValueChange = {},
                readOnly    = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currExpanded) },
                modifier    = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                shape       = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = currExpanded, onDismissRequest = { currExpanded = false }) {
                app.fynlo.logic.CurrencyUtils.supported.forEach { c ->
                    DropdownMenuItem(
                        text    = { Text("${c.symbol}  ${c.code}  ${c.name}") },
                        onClick = { currency = c.code; currExpanded = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Color")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEach { hex ->
                // 3.2.65 — fallback for unparseable hex was Color.Blue
                // (#FF0000FF), which read jarringly bright in dark mode.
                // Theme primary keeps the fallback visually grounded.
                val primaryFallback = MaterialTheme.colorScheme.primary
                val parsed = runCatching {
                    Color(android.graphics.Color.parseColor(hex))
                }.getOrDefault(primaryFallback)
                Surface(
                    modifier  = Modifier.size(36.dp),
                    shape     = RoundedCornerShape(10.dp),
                    color     = parsed,
                    onClick   = { selectedColor = hex }
                ) {
                    if (selectedColor == hex) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    onConfirm(
                        Project(
                            id          = app.fynlo.logic.Ids.newId(),
                            name        = name.trim(),
                            icon        = iconKey,
                            color       = selectedColor,
                            currency    = currency.trim().ifBlank { "INR" },
                            createdAt   = today,
                            description = description.trim(),
                        )
                    )
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = app.fynlo.ui.theme.Emerald500),
        ) {
            Text("Create Project", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(
            if (name.isBlank()) "Enter a project name to continue" else null
        )
    }
}



