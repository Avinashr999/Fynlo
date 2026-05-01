package com.example.cashmemo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.data.model.FlowResult
import com.example.cashmemo.logic.FlowRuleEngine
import com.example.cashmemo.ui.components.WizardStepIndicator
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val STEP_LABELS = listOf("Event", "Details", "Route", "Confirm")

@Composable
fun SmartFlowWizardScreen(
    viewModel: FinanceViewModel,
    onDone: () -> Unit
) {
    val transactions     by viewModel.transactions.collectAsState()
    val accounts         by viewModel.accounts.collectAsState()
    val currentProjectId by viewModel.currentProjectId.collectAsState()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    // ── Wizard state ──────────────────────────────────────────────────────────
    var step         by remember { mutableIntStateOf(0) }
    var eventType    by remember { mutableStateOf("") }
    var amount       by remember { mutableStateOf("") }
    var description  by remember { mutableStateOf("") }
    var category     by remember { mutableStateOf("") }
    var fromAccount  by remember { mutableStateOf("") }
    var toAccount    by remember { mutableStateOf("") }
    var personName   by remember { mutableStateOf("") }
    var personPhone  by remember { mutableStateOf("") }
    var notes        by remember { mutableStateOf("") }

    val accountNames  = accounts.map { it.name }
    val topCategories = FlowRuleEngine.topCategories(transactions)
    val suggestedFrom = if (eventType.isNotBlank())
        FlowRuleEngine.suggestSourceAccount(transactions, eventType) else ""
    val suggestedCat  = if (eventType.isNotBlank() && category.isBlank())
        FlowRuleEngine.suggestAccountForCategory(transactions, category) else ""

    // Pre-fill suggested account when event type chosen
    LaunchedEffect(eventType) {
        if (fromAccount.isBlank() && suggestedFrom.isNotBlank()) fromAccount = suggestedFrom
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                "Smart Flow Wizard",
                style    = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Connected money tracking in 4 steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            WizardStepIndicator(
                steps       = STEP_LABELS,
                currentStep = step,
                modifier    = Modifier.padding(bottom = 28.dp)
            )

            AnimatedContent(
                targetState   = step,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "wizard_step"
            ) { currentStep ->
                when (currentStep) {
                    0 -> Step1EventType(
                        selected  = eventType,
                        onSelect  = { eventType = it; step = 1 }
                    )
                    1 -> Step2Details(
                        eventType    = eventType,
                        amount       = amount,
                        onAmountChange = { amount = it },
                        description  = description,
                        onDescChange = { description = it },
                        category     = category,
                        onCatChange  = { category = it },
                        topCategories = topCategories,
                        personName   = personName,
                        onPersonName = { personName = it },
                        personPhone  = personPhone,
                        onPersonPhone = { personPhone = it },
                        notes        = notes,
                        onNotes      = { notes = it }
                    )
                    2 -> Step3Route(
                        eventType    = eventType,
                        accountNames = accountNames,
                        fromAccount  = fromAccount,
                        onFromChange = { fromAccount = it },
                        toAccount    = toAccount,
                        onToChange   = { toAccount = it },
                        suggestedFrom = suggestedFrom
                    )
                    3 -> Step4Confirm(
                        eventType   = eventType,
                        amount      = amount.toDoubleOrNull() ?: 0.0,
                        description = description,
                        category    = category,
                        fromAccount = fromAccount,
                        toAccount   = toAccount,
                        personName  = personName,
                        notes       = notes,
                        date        = today
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Navigation buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick  = { step-- },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape    = RoundedCornerShape(14.dp)
                    ) { Text("Back") }
                }

                val isLastStep    = step == 3
                val canProceed    = when (step) {
                    0 -> eventType.isNotBlank()
                    1 -> amount.toDoubleOrNull() != null && amount.toDouble() > 0
                    2 -> fromAccount.isNotBlank() || toAccount.isNotBlank()
                    else -> true
                }

                Button(
                    onClick  = {
                        if (isLastStep) {
                            val result = FlowResult(
                                eventType   = eventType,
                                amount      = amount.toDoubleOrNull() ?: 0.0,
                                description = description,
                                fromAccount = fromAccount,
                                toAccount   = toAccount,
                                category    = category.ifBlank { eventType },
                                date        = today,
                                personName  = personName,
                                personPhone = personPhone,
                                notes       = notes,
                                projectId   = currentProjectId
                            )
                            viewModel.executeFlow(result)
                            onDone()
                        } else {
                            step++
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    enabled  = canProceed
                ) {
                    Text(if (isLastStep) "Save All" else "Next →", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
// ── Step 1: Choose event type ─────────────────────────────────────────────────

private data class EventOption(val label: String, val icon: ImageVector, val color: Color, val hint: String)

private val EVENT_OPTIONS = listOf(
    EventOption("Received",  Icons.Default.ArrowDownward,   Color(0xFF2E7D32), "Money came in"),
    EventOption("Spent",     Icons.Default.ArrowUpward,     Color(0xFFC62828), "Money went out"),
    EventOption("Moved",     Icons.Default.SwapHoriz,       Color(0xFF1565C0), "Transfer between accounts"),
    EventOption("Lent",      Icons.Default.Handshake,       Color(0xFFF57F17), "Lent money to someone"),
    EventOption("Borrowed",  Icons.Default.CreditCard,      Color(0xFF6A1B9A), "Borrowed money from someone")
)

@Composable
private fun Step1EventType(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("What happened?", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(bottom = 4.dp))
        EVENT_OPTIONS.forEach { opt ->
            val isSelected = opt.label == selected
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(opt.label) },
                shape    = RoundedCornerShape(14.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = if (isSelected) opt.color.copy(alpha = 0.12f)
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border   = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, opt.color) else null
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(opt.icon, contentDescription = null, tint = opt.color, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(opt.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(opt.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = opt.color)
                }
            }
        }
    }
}

// ── Step 2: Amount, category, person ─────────────────────────────────────────

@Composable
private fun Step2Details(
    eventType: String, amount: String, onAmountChange: (String) -> Unit,
    description: String, onDescChange: (String) -> Unit,
    category: String, onCatChange: (String) -> Unit, topCategories: List<String>,
    personName: String, onPersonName: (String) -> Unit,
    personPhone: String, onPersonPhone: (String) -> Unit,
    notes: String, onNotes: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Enter details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        OutlinedTextField(
            value = amount, onValueChange = onAmountChange,
            label = { Text("Amount (₹)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        OutlinedTextField(
            value = description, onValueChange = onDescChange,
            label = { Text("Description") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        if (eventType in listOf("Received", "Spent")) {
            Text("Category", style = MaterialTheme.typography.labelMedium)
            if (topCategories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(topCategories) { cat ->
                        FilterChip(selected = cat == category, onClick = { onCatChange(cat) }, label = { Text(cat) })
                    }
                }
            }
            OutlinedTextField(
                value = category, onValueChange = onCatChange,
                label = { Text("Or type category") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
        }
        if (eventType in listOf("Lent", "Borrowed")) {
            OutlinedTextField(value = personName, onValueChange = onPersonName, label = { Text("Person name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = personPhone, onValueChange = onPersonPhone, label = { Text("Phone (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        }
        OutlinedTextField(value = notes, onValueChange = onNotes, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), maxLines = 3)
    }
}

// ── Step 3: Route money ───────────────────────────────────────────────────────

@Composable
private fun Step3Route(
    eventType: String, accountNames: List<String>,
    fromAccount: String, onFromChange: (String) -> Unit,
    toAccount: String, onToChange: (String) -> Unit,
    suggestedFrom: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Route the money", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        val needsFrom = eventType in listOf("Spent", "Lent", "Moved")
        val needsTo   = eventType in listOf("Received", "Borrowed", "Moved")
        if (needsFrom) {
            Text("From account", style = MaterialTheme.typography.labelMedium)
            if (suggestedFrom.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Suggested: $suggestedFrom", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                }
            }
            AccountChipRow(accounts = accountNames, selected = fromAccount, onSelect = onFromChange)
        }
        if (needsTo) {
            Text("To account", style = MaterialTheme.typography.labelMedium)
            AccountChipRow(accounts = accountNames, selected = toAccount, onSelect = onToChange)
        }
        if (accountNames.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text("No accounts found. Add an account in Settings first.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccountChipRow(accounts: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(accounts) { name ->
            FilterChip(selected = name == selected, onClick = { onSelect(name) }, label = { Text(name) })
        }
    }
}

// ── Step 4: Confirm preview ───────────────────────────────────────────────────

@Composable
private fun Step4Confirm(
    eventType: String, amount: Double, description: String, category: String,
    fromAccount: String, toAccount: String, personName: String, notes: String, date: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Review & confirm", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfirmRow("Event",       eventType)
                ConfirmRow("Amount",      "₹ ${"%.2f".format(amount)}")
                if (description.isNotBlank()) ConfirmRow("Description", description)
                if (category.isNotBlank())    ConfirmRow("Category",    category)
                if (fromAccount.isNotBlank()) ConfirmRow("From",        fromAccount)
                if (toAccount.isNotBlank())   ConfirmRow("To",          toAccount)
                if (personName.isNotBlank())  ConfirmRow("Person",      personName)
                if (notes.isNotBlank())       ConfirmRow("Notes",       notes)
                ConfirmRow("Date",        date)
            }
        }
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (eventType) {
                        "Lent"     -> "Will create a Borrower record + an Expense transaction from $fromAccount."
                        "Borrowed" -> "Will create a Debt record + an Income transaction into $toAccount."
                        "Moved"    -> "Will create a Transfer: $fromAccount → $toAccount."
                        "Received" -> "Will add Income to $toAccount."
                        else       -> "Will record an Expense from $fromAccount."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
