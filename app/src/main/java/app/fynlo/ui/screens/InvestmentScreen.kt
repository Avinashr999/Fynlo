package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Investment
import app.fynlo.logic.DateUtils
import app.fynlo.ui.components.AddInvestmentDialog
import app.fynlo.ui.components.InvestmentSaveRequest
import java.util.Locale

@Composable
fun InvestmentScreen(viewModel: FinanceViewModel) {
    val investments    by viewModel.investments.collectAsState()
    val accounts       by viewModel.accounts.collectAsState()
    val debts          by viewModel.debts.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")

    var editingInvest  by remember { mutableStateOf<Investment?>(null) }
    var updatingInvest by remember { mutableStateOf<Investment?>(null) }
    var deletingInvest by remember { mutableStateOf<Investment?>(null) }

    // ── Edit dialog ────────────────────────────────────────────────────────────
    if (editingInvest != null) {
        AddInvestmentDialog(
            accounts = accounts,
            debts    = debts,
            onDismiss = { editingInvest = null },
            onConfirm = { req: InvestmentSaveRequest ->
                if (editingInvest?.id?.isNotBlank() == true) {
                    viewModel.updateInvestment(req.investment)
                } else {
                    when (req.sourceType) {
                        "account"       -> viewModel.addInvestmentFundedByAccount(req.investment, req.sourceAccountName)
                        "existing_debt" -> req.sourceDebt?.let { viewModel.addInvestmentFundedByExistingDebt(req.investment, it) }
                        "new_loan"      -> req.newLoan?.let { viewModel.addInvestmentFundedByNewLoan(req.investment, it) }
                        else            -> viewModel.addInvestmentWithSource(req.investment, req.sourceAccountName)
                    }
                }
                editingInvest = null
            },
            initialInvestment = editingInvest
        )
    }

    // ── Update value dialog ────────────────────────────────────────────────────
    if (updatingInvest != null) {
        UpdateInvestmentValueDialog(
            investment = updatingInvest!!,
            currencySymbol = currencySymbol,
            onDismiss  = { updatingInvest = null },
            onConfirm  = { newVal ->
                viewModel.updateInvestmentValue(updatingInvest!!, newVal)
                updatingInvest = null
            }
        )
    }

    // ── Smart delete confirmation — shows options based on sourceType ──────────
    if (deletingInvest != null) {
        val inv = deletingInvest!!
        AlertDialog(
            onDismissRequest = { deletingInvest = null },
            title = { Text("Delete Investment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${inv.name}  •  ₹${String.format(java.util.Locale.getDefault(), "%,.0f", inv.invested)}")
                    Spacer(Modifier.height(4.dp))
                    when (inv.sourceType) {
                        "account"  -> Text("This was funded from ${inv.fundingSource}. Do you also want to restore ₹${String.format("%,.0f", inv.invested)} back to that account?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        "new_loan" -> Text("This investment has a linked loan (${inv.fundingSource}). Do you want to delete the loan record too?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else       -> Text("This will permanently remove the investment record.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                    when (inv.sourceType) {
                        "account" -> {
                            Button(onClick = { viewModel.deleteInvestmentAndReverseAccount(inv); deletingInvest = null }) {
                                Text("Delete + Restore to ${inv.fundingSource.take(12)}")
                            }
                        }
                        "new_loan" -> {
                            Button(
                                onClick = { viewModel.deleteInvestmentAndLinkedLoan(inv); deletingInvest = null },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete Investment + Loan")
                            }
                        }
                        else -> {}
                    }
                    OutlinedButton(onClick = { viewModel.deleteInvestment(inv); deletingInvest = null }) {
                        Text("Delete Record Only")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingInvest = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "My Investments", 
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (investments.isEmpty()) {
            EmptyInvestState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(investments) { invest ->
                    InvestmentCard(
                        invest   = invest,
                        currencySymbol = currencySymbol,
                        onDelete = { deletingInvest = invest },
                        onEdit   = { editingInvest = invest },
                        onUpdate = { updatingInvest = invest }
                    )
                }
            }
        }
    }
}

@Composable
fun InvestmentCard(invest: Investment, currencySymbol: String = "₹", onDelete: () -> Unit, onEdit: () -> Unit, onUpdate: () -> Unit) {
    val growth = invest.currentVal - invest.invested
    val growthPercent = if (invest.invested > 0) (growth / invest.invested) * 100 else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null, 
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        invest.name, 
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = Color.Red.copy(alpha = 0.6f))
                    }
                    FilledTonalButton(
                        onClick = onUpdate,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) { Text("Update", style = MaterialTheme.typography.labelSmall) }
                    Badge(
                        containerColor = if (growth >= 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        contentColor = if (growth >= 0) Color(0xFF059669) else Color.Red
                    ) {
                        Text("${String.format(Locale.getDefault(), "%.1f", growthPercent)}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Invested Principal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", invest.invested)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Date: ${DateUtils.formatToDisplay(invest.date)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Value", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$currencySymbol ${String.format(Locale.getDefault(), "%,.0f", invest.currentVal)}", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = Color(0xFF059669)))
                    Text("Type: ${invest.type}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            if (invest.notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Text(invest.notes, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun EmptyInvestState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        Text("No investments yet", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
    }
}

@Composable
fun UpdateInvestmentValueDialog(
    investment: Investment,
    currencySymbol: String = "₹",
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var newValue by remember { mutableStateOf(investment.currentVal.toBigDecimal().stripTrailingZeros().toPlainString()) }
    val parsed   = newValue.toDoubleOrNull()
    val growth   = parsed?.let { it - investment.invested }
    val locale   = java.util.Locale.getDefault()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Market Value") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(investment.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Invested: $currencySymbol ${String.format(locale, "%,.2f", investment.invested)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = newValue, onValueChange = { newValue = it },
                    label = { Text("Current Market Value ($currencySymbol)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                if (growth != null) {
                    val pct = if (investment.invested > 0) (growth / investment.invested) * 100 else 0.0
                    Text(
                        "${if (growth >= 0) "+" else ""}$currencySymbol ${String.format(locale, "%,.2f", growth)} (${String.format(locale, "%.1f", pct)}%)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = if (growth >= 0) Color(0xFF059669) else Color(0xFFEF4444)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let { onConfirm(it) } }, enabled = parsed != null) {
                Text("Update")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}








