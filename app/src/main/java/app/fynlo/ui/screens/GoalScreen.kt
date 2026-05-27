package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Goal
import app.fynlo.logic.CurrencyFormatter
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@Composable
fun GoalScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val goals by viewModel.goals.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    val locale = remember { Locale.getDefault() }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddGoalDialog(
            currencySymbol = currencySymbol,
            onDismiss = { showAddDialog = false },
            onConfirm = { goal ->
                viewModel.addGoal(goal)
                showAddDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Savings Goals", "Track your financial targets")
        Box(modifier = Modifier.weight(1f)) {
            // C07 fix (UX_AUDIT §C07): on empty state show ONLY the shared
            // EmptyState CTA, hiding both the list AND the FAB so the user
            // sees one unambiguous "Add First Goal" entry point — not the
            // pre-3.2.12 triple of header FAB + inline button + Scaffold FAB.
            if (goals.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Star,
                    title = "No savings goals yet",
                    subtitle = "Set targets for big purchases or milestones",
                    actionLabel = "Add First Goal",
                    onAction = { showAddDialog = true },
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = FabBottomPadding)
                ) {
                    item {
                        Text(
                            "Track your progress towards big purchases or milestones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(goals, key = { _, g -> g.id }) { index, goal ->
                        GoalCard(goal, currencyCode, locale, onDelete = { viewModel.deleteGoal(goal) })
                        if (index < goals.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Goal")
                }
            }
        }
    }
}

@Composable
fun GoalCard(goal: Goal, currencyCode: String, locale: Locale, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete goal?") },
            text  = { Text("Remove the \"${goal.name}\" savings goal? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    val progress    = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val pct         = (progress * 100).toInt()
    val isComplete  = pct >= 100
    val accentColor = if (isComplete) Emerald500 else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Star, null, Modifier.size(20.dp), tint = SemanticAmber)
                    Text(goal.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (isComplete) {
                        Surface(color = Emerald500.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                            Text("COMPLETE", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Emerald500,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = SemanticRed.copy(alpha = 0.6f))
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress     = { progress },
                modifier     = Modifier.fillMaxWidth().height(8.dp),
                color        = if (isComplete) Emerald500 else MaterialTheme.colorScheme.primary,
                trackColor   = accentColor.copy(alpha = 0.15f),
                strokeCap    = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${CurrencyFormatter.detail(goal.savedAmount, currencyCode, locale)} saved",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Emerald500)
                Text("${CurrencyFormatter.detail(goal.targetAmount, currencyCode, locale)} target • $pct%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // C22 Stage 2 — surface target date when set so the user sees
            // their deadline at a glance. Empty deadline stays hidden.
            if (goal.deadline.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Target by ${app.fynlo.logic.DateUtils.formatToDisplay(goal.deadline)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
}

@Composable
fun AddGoalDialog(currencySymbol: String, onDismiss: () -> Unit, onConfirm: (Goal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    // C22 Stage 2 (3.2.47) — target-date field per audit §C22 #207. Goal
    // model already had a `deadline: String = ""` field (yyyy-MM-dd or
    // blank) — just wasn't exposed by the dialog before. Empty stays
    // valid for goals without a deadline.
    var deadline by remember { mutableStateOf("") }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text("Add Savings Goal") },
        text = {
            // C22 Stage 2 cross-dialog sweep (3.2.51) — see RecurringScreen
            // AddRecurringDialog comment for rationale. AlertDialog.text
            // doesn't auto-scroll; wrap in verticalScroll so 4 OutlinedTextFields
            // (Name + Target + Saved + Deadline) never clip.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal Name (e.g., New Car)") })
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target Amount ($currencySymbol)") })
                OutlinedTextField(value = saved, onValueChange = { saved = it }, label = { Text("Already Saved ($currencySymbol)") })
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Target Date (yyyy-MM-dd, optional)") },
                    placeholder = { Text("e.g. 2026-12-31") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(Goal(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    targetAmount = target.toDoubleOrNull() ?: 0.0,
                    savedAmount = saved.toDoubleOrNull() ?: 0.0,
                    // Pass through deadline as user-typed; if blank or
                    // malformed it stays as-is. Goal.deadline default = ""
                    // so empty input is preserved.
                    deadline = deadline.trim()
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
