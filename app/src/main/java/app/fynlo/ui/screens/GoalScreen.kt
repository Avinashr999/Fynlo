package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import java.util.*
import app.fynlo.ui.theme.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback


@Composable
fun GoalScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val goals by viewModel.goals.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            item {
                PremiumScreenHeader("Savings Goals", "Track your financial targets")
                Text(
                    "Track your progress towards big purchases or milestones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (goals.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Star, null, Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant)
                            Text("No savings goals yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Set targets for big purchases or milestones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outlineVariant)
                            Button(onClick = { showAddDialog = true }) { Text("Add First Goal") }
                        }
                    }
                }
            } else {
                items(goals) { goal ->
                    GoalCard(goal, currencySymbol, onDelete = { viewModel.deleteGoal(goal) })
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

@Composable
fun GoalCard(goal: Goal, currencySymbol: String, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val progress    = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val pct         = (progress * 100).toInt()
    val isComplete  = pct >= 100
    val accentColor = if (isComplete) Emerald500 else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDelete() }, modifier = Modifier.size(32.dp)) {
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
                Text("$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", goal.savedAmount)} saved",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Emerald500)
                Text("$currencySymbol${String.format(java.util.Locale.getDefault(), "%,.0f", goal.targetAmount)} target • $pct%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AddGoalDialog(currencySymbol: String, onDismiss: () -> Unit, onConfirm: (Goal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Savings Goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Goal Name (e.g., New Car)") })
                OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target Amount ($currencySymbol)") })
                OutlinedTextField(value = saved, onValueChange = { saved = it }, label = { Text("Already Saved ($currencySymbol)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(Goal(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    targetAmount = target.toDoubleOrNull() ?: 0.0,
                    savedAmount = saved.toDoubleOrNull() ?: 0.0
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
