package app.fynlo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Investment
import app.fynlo.logic.DateUtils
import app.fynlo.ui.components.DatePickerField
import app.fynlo.ui.components.WizardStepIndicator
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFlowWizardScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1: Asset Details
    var assetName by remember { mutableStateOf("") }
    var assetType by remember { mutableStateOf("Stocks") }
    var investedAmt by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    
    // Step 2: Funding Source
    val sources = listOf("Existing Account", "New Bank Loan", "Already Settled (Historical)")
    var selectedSource by remember { mutableStateOf(sources[0]) }
    var accountName by remember { mutableStateOf("") } // For Existing Account
    
    // For New Bank Loan
    var lenderName by remember { mutableStateOf("") }
    var loanInterestRate by remember { mutableStateOf("12.0") }
    var loanTenure by remember { mutableStateOf("12") }

    val accounts by viewModel.accounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal Entry Wizard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            WizardStepIndicator(currentStep = currentStep, totalSteps = 3)
            Spacer(Modifier.height(32.dp))

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                        } else {
                            slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }.using(SizeTransform(clip = false))
                    },
                    label = "wizard_step"
                ) { step ->
                    when (step) {
                        1 -> StepAssetDetails(
                            name = assetName, onNameChange = { assetName = it },
                            type = assetType, onTypeChange = { assetType = it },
                            amt = investedAmt, onAmtChange = { investedAmt = it },
                            date = purchaseDate, onDateChange = { purchaseDate = it }
                        )
                        2 -> StepFundingSource(
                            sources = sources,
                            selected = selectedSource,
                            onSourceChange = { selectedSource = it },
                            accounts = accounts.map { it.name },
                            selectedAcct = accountName,
                            onAcctChange = { accountName = it },
                            lender = lenderName,
                            onLenderChange = { lenderName = it },
                            rate = loanInterestRate,
                            onRateChange = { loanInterestRate = it },
                            tenure = loanTenure,
                            onTenureChange = { loanTenure = it }
                        )
                        3 -> StepVerification(
                            assetName = assetName,
                            amt = investedAmt.toDoubleOrNull() ?: 0.0,
                            source = selectedSource,
                            sourceName = if (selectedSource == sources[0]) accountName else lenderName
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Previous")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (currentStep < 3) {
                    Button(
                        onClick = { currentStep++ },
                        enabled = isStepValid(currentStep, assetName, investedAmt, selectedSource, accountName, lenderName),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            val inv = Investment(
                                id = UUID.randomUUID().toString(),
                                name = assetName,
                                type = assetType,
                                invested = investedAmt.toDoubleOrNull() ?: 0.0,
                                currentVal = investedAmt.toDoubleOrNull() ?: 0.0,
                                date = DateUtils.parseInput(purchaseDate),
                                notes = "Logged via Smart Wizard"
                            )
                            
                            val debt = if (selectedSource == sources[1]) {
                                Debt(
                                    id = UUID.randomUUID().toString(),
                                    name = lenderName,
                                    amount = investedAmt.toDoubleOrNull() ?: 0.0,
                                    rate = loanInterestRate.toDoubleOrNull() ?: 0.0,
                                    date = DateUtils.parseInput(purchaseDate),
                                    notes = "Loan for $assetName",
                                    intType = "Simple Interest"
                                )
                            } else null

                            val finalSourceType = when(selectedSource) {
                                sources[0] -> "Account"
                                sources[1] -> "Debt"
                                else -> "Already Settled"
                            }

                            viewModel.executeLinkedInvestment(
                                investment = inv,
                                fundingSourceType = finalSourceType,
                                sourceName = if (finalSourceType == "Account") accountName else lenderName,
                                debtDetails = debt
                            )
                            onNavigateBack()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Confirm & Execute")
                    }
                }
            }
        }
    }
}

private fun isStepValid(step: Int, name: String, amt: String, source: String, acct: String, lender: String): Boolean {
    return when(step) {
        1 -> name.isNotBlank() && (amt.toDoubleOrNull() ?: 0.0) > 0
        2 -> when(source) {
            "Existing Account" -> acct.isNotBlank()
            "New Bank Loan" -> lender.isNotBlank()
            else -> true
        }
        else -> true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepAssetDetails(
    name: String, onNameChange: (String) -> Unit,
    type: String, onTypeChange: (String) -> Unit,
    amt: String, onAmtChange: (String) -> Unit,
    date: String, onDateChange: (String) -> Unit
) {
    val types = listOf("Stocks", "Mutual Funds", "Gold", "Business", "Real Estate", "Other")
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("What did you acquire?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("Asset Name (e.g. HDFC Bank Eq)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = type, onValueChange = {}, readOnly = true,
                label = { Text("Asset Class") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                types.forEach { t ->
                    DropdownMenuItem(text = { Text(t) }, onClick = { onTypeChange(t); expanded = false })
                }
            }
        }

        OutlinedTextField(
            value = amt, onValueChange = onAmtChange,
            label = { Text("Purchase Amount (Historical Cost)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        DatePickerField(value = date, onValueChange = onDateChange, label = "Transaction Date")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepFundingSource(
    sources: List<String>,
    selected: String,
    onSourceChange: (String) -> Unit,
    accounts: List<String>,
    selectedAcct: String,
    onAcctChange: (String) -> Unit,
    lender: String,
    onLenderChange: (String) -> Unit,
    rate: String,
    onRateChange: (String) -> Unit,
    tenure: String,
    onTenureChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("How was this funded?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        sources.forEach { src ->
            Surface(
                onClick = { onSourceChange(src) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected == src) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                border = if (selected == src) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (selected == src), onClick = { onSourceChange(src) })
                    Spacer(Modifier.width(12.dp))
                    Text(src, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (selected == sources[0]) {
            var exp by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = exp, onExpandedChange = { exp = !exp }) {
                OutlinedTextField(
                    value = selectedAcct, onValueChange = {}, readOnly = true,
                    label = { Text("Select Bank / Cash Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                    accounts.forEach { a ->
                        DropdownMenuItem(text = { Text(a) }, onClick = { onAcctChange(a); exp = false })
                    }
                }
            }
        } else if (selected == sources[1]) {
            OutlinedTextField(
                value = lender, onValueChange = onLenderChange,
                label = { Text("Lender Name (Bank/Person)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = rate, onValueChange = onRateChange,
                    label = { Text("Int. Rate (%)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = tenure, onValueChange = onTenureChange,
                    label = { Text("Tenure (Mo)") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        } else {
            Surface(
                color = Color(0xFFFDE68A).copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFD97706))
                    Spacer(Modifier.width(12.dp))
                    Text("Choosing this will NOT deduct money from your current accounts. Perfect for historical data.", 
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E))
                }
            }
        }
    }
}

@Composable
fun StepVerification(
    assetName: String,
    amt: Double,
    source: String,
    sourceName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Double-Entry Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryRow("Debit (Asset)", assetName, "+ ₹${String.format("%,.0f", amt)}", Color(0xFF059669))
                
                val creditLabel = when(source) {
                    "Existing Account" -> "Cash/Bank: $sourceName"
                    "New Bank Loan" -> "Liability: $sourceName"
                    else -> "Equity (Historical)"
                }
                EntryRow("Credit (Source)", creditLabel, "- ₹${String.format("%,.0f", amt)}", if (source == "New Bank Loan") Color(0xFFEF4444) else Color(0xFF3B82F6))
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Impact on Net Worth", style = MaterialTheme.typography.bodySmall)
                    Text("₹ 0 (Neutral)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Text("By clicking confirm, the app will execute these balanced ledger entries simultaneously to maintain financial integrity.", 
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(side: String, label: String, valStr: String, color: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Column {
            Text(side, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Text(valStr, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
