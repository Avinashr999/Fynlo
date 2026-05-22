package app.fynlo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
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
                viewModel.createProject(project)
                viewModel.switchProject(project.id)
                showAddDialog = false
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
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(
                "Your Projects",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            FilledTonalButton(onClick = {
                if (isPro || projects.isEmpty()) showAddDialog = true else onNavigateToUpgrade()
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Project")
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(projects, key = { _, it -> it.id }) { index, project ->
                ProjectCard(
                    project   = project,
                    isActive  = project.id == currentPid,
                    onSelect  = { viewModel.switchProject(project.id) },
                    onDelete  = {
                        if (project.id != "personal") deleteTarget = project
                    }
                )
                if (index < projects.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
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
    onDelete: () -> Unit
) {
        Row(
            modifier          = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color dot
            Surface(
                modifier = Modifier.size(42.dp),
                shape    = RoundedCornerShape(12.dp),
                color    = runCatching { Color(android.graphics.Color.parseColor(project.color)) }
                              .getOrDefault(MaterialTheme.colorScheme.primary)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector        = Icons.Default.Business,
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
                Text(
                    text  = project.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint               = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
            }

            // Cannot delete the default personal project
            if (project.id != "personal") {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete project",
                        tint               = MaterialTheme.colorScheme.error
                    )
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
    var currency      by remember { mutableStateOf("INR") }
    var currExpanded  by remember { mutableStateOf(false) }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val colors = listOf("#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899")
    var selectedColor by remember { mutableStateOf(colors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Project name") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                // Currency dropdown
                ExposedDropdownMenuBox(expanded = currExpanded, onExpandedChange = { currExpanded = !currExpanded }) {
                    OutlinedTextField(
                        value       = "${app.fynlo.logic.CurrencyUtils.symbolFor(currency)} $currency",
                        onValueChange = {},
                        readOnly    = true,
                        label       = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(currExpanded) },
                        modifier    = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
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
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { hex ->
                        val parsed = runCatching {
                            Color(android.graphics.Color.parseColor(hex))
                        }.getOrDefault(Color.Blue)
                        Surface(
                            modifier  = Modifier.size(32.dp),
                            shape     = RoundedCornerShape(8.dp),
                            color     = parsed,
                            onClick   = { selectedColor = hex }
                        ) {
                            if (selectedColor == hex) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            Project(
                                id        = UUID.randomUUID().toString(),
                                name      = name.trim(),
                                color     = selectedColor,
                                currency  = currency.trim().ifBlank { "INR" },
                                createdAt = today
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}









