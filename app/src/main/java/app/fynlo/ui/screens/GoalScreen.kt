package app.fynlo.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalLocale


@Composable
fun GoalScreen(viewModel: FinanceViewModel) {
    val haptic = LocalHapticFeedback.current
    val goals by viewModel.goals.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)
    val locale = LocalLocale.current.platformLocale
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddGoalDialog(
            currencySymbol = currencySymbol,
            accounts = accounts.map { it.name },
            onDismiss = { showAddDialog = false },
            onConfirm = { goal ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.addGoal(goal)
                viewModel.showFeedback("Goal added")
                showAddDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Savings Goals", subtitle = "Track your financial targets")
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
                    onAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAddDialog = true
                    },
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
                        GoalCard(goal, currencyCode, locale, onDelete = {
                            viewModel.deleteGoal(goal)
                            viewModel.showFeedback("Goal deleted")
                        })
                        if (index < goals.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        }
                    }
                }
                FloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAddDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).size(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    containerColor = Emerald500,
                    contentColor = Color.White,
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
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    showDeleteConfirm = false
                },
                    colors = ButtonDefaults.textButtonColors(contentColor = SemanticRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
    val progress    = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val pct         = (progress * 100).toInt()
    val isComplete  = pct >= 100
    val accentColor = if (isComplete) Emerald500 else MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth().animateContentSize().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // C22 (3.2.55) — render the user-picked icon (defaults to
                    // Star for legacy goals). iconFor() falls back to Star on
                    // unknown keys so a future-version goal opened on an older
                    // build still renders.
                    Icon(
                        app.fynlo.ui.components.GoalIcons.iconFor(goal.iconKey),
                        null, Modifier.size(20.dp), tint = SemanticAmber
                    )
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
            // C22 (3.2.55) — linkedAccount badge. Saved-amount math is
            // unchanged for this stage; the badge is informational only.
            if (goal.linkedAccount.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Linked to: ${goal.linkedAccount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    currencySymbol: String,
    accounts: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (Goal) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    // C22 Stage 2 (3.2.47) — target-date field per audit §C22 #207.
    var deadline by remember { mutableStateOf("") }
    // C22 (3.2.55) — icon picker + linked-account dropdown.
    var iconKey by remember { mutableStateOf("star") }
    var linkedAccount by remember { mutableStateOf("") }
    var acctExpanded by remember { mutableStateOf(false) }

    // C22 dialog universalization (3.2.53) — migrated from AlertDialog to
    // the canonical FormDialog pattern (Lending-style). Bold section labels,
    // bottom full-width primary button, top-right X close. Matches every
    // other migrated form dialog visually.
    app.fynlo.ui.components.FormDialog(
        title = "Add Savings Goal",
        onDismiss = onDismiss,
    ) {
        val targetNum  = target.toDoubleOrNull() ?: 0.0
        val disabledReason: String? = when {
            name.isBlank()  -> "Enter a goal name to continue"
            targetNum <= 0.0 -> "Enter a positive target amount to continue"
            else            -> null
        }

        app.fynlo.ui.components.FormSectionLabel("Goal name")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            placeholder = { Text("e.g. New Car") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Target amount ($currencySymbol)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = target, onValueChange = { target = it },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Already saved ($currencySymbol, optional)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = saved, onValueChange = { saved = it },
            placeholder = { Text("0") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Target date (optional)")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = deadline, onValueChange = { deadline = it },
            placeholder = { Text("e.g. 2026-12-31") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        // C22 (3.2.55) — icon picker. 6 curated icons; default "star" matches
        // the pre-3.2.55 look so an unchanged tap-Save still renders the same.
        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Icon")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.fynlo.ui.components.GoalIcons.all.forEach { opt ->
                val selected = iconKey == opt.key
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) SemanticAmber.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    onClick = { iconKey = opt.key },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            opt.icon,
                            contentDescription = opt.label,
                            tint = if (selected) SemanticAmber
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // C22 (3.2.55) — linked account dropdown. Optional; "None" means no
        // link. When an account is picked, the goal card surfaces a "Linked
        // to: X" badge. Auto-deduct on contribution is a separate stage —
        // for now this is metadata-only.
        if (accounts.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            app.fynlo.ui.components.FormSectionLabel("Linked account (optional)")
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = acctExpanded,
                onExpandedChange = { acctExpanded = !acctExpanded }
            ) {
                OutlinedTextField(
                    value = linkedAccount.ifBlank { "None" },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = acctExpanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = acctExpanded,
                    onDismissRequest = { acctExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { linkedAccount = ""; acctExpanded = false }
                    )
                    accounts.forEach { acct ->
                        DropdownMenuItem(
                            text = { Text(acct) },
                            onClick = { linkedAccount = acct; acctExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                onConfirm(Goal(
                    id = app.fynlo.logic.Ids.newId(),
                    name = name.trim(),
                    targetAmount = targetNum,
                    savedAmount = saved.toDoubleOrNull() ?: 0.0,
                    deadline = deadline.trim(),
                    iconKey = iconKey,
                    linkedAccount = linkedAccount.trim(),
                ))
            },
            enabled = disabledReason == null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Save Goal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        app.fynlo.ui.components.DisabledButtonHint(disabledReason)
    }
}
