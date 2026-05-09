package app.fynlo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Investment
import app.fynlo.logic.DateUtils
import app.fynlo.ui.components.AddInvestmentDialog
import java.util.*

@Composable
fun InvestmentScreen(viewModel: FinanceViewModel) {
    val investments by viewModel.investments.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currentProject?.currency ?: "INR")
    var editingInvest  by remember { mutableStateOf<Investment?>(null) }
    var updatingInvest by remember { mutableStateOf<Investment?>(null) }
    var viewingHistory by remember { mutableStateOf<Investment?>(null) }

    if (editingInvest != null) {
        AddInvestmentDialog(
            onDismiss = { editingInvest = null },
            onConfirm = { invest, source ->
                if (editingInvest?.id?.isNotBlank() == true) {
                    viewModel.updateInvestment(invest)
                } else {
                    viewModel.addInvestmentWithSource(invest, source)
                }
                editingInvest = null
            },
            initialInvestment = editingInvest
        )
    }

    if (updatingInvest != null) {
        UpdateInvestmentValueDialog(
            investment = updatingInvest!!,
            currencySymbol = currencySymbol,
            onDismiss  = { updatingInvest = null },
            onConfirm  = { newVal, date, notes ->
                viewModel.addValuation(
                    app.fynlo.data.model.InvestmentValuation(
                        id = UUID.randomUUID().toString(),
                        investmentId = updatingInvest!!.id,
                        date = DateUtils.parseInput(date),
                        value = newVal,
                        notes = notes
                    )
                )
                updatingInvest = null
            }
        )
    }

    if (viewingHistory != null) {
        ValuationHistoryDialog(
            investment = viewingHistory!!,
            currencySymbol = currencySymbol,
            valuations = viewModel.getValuationsForInvestment(viewingHistory!!.id).collectAsState(initial = emptyList()).value,
            onDismiss = { viewingHistory = null }
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
                        onDelete = { viewModel.deleteInvestment(invest) },
                        onEdit   = { editingInvest = invest },
                        onUpdate = { updatingInvest = invest },
                        onViewHistory = { viewingHistory = invest }
                    )
                }
            }
        }
    }
}

@Composable
fun InvestmentCard(invest: Investment, currencySymbol: String = "₹", onDelete: () -> Unit, onEdit: () -> Unit, onUpdate: () -> Unit, onViewHistory: () -> Unit) {
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
                    IconButton(onClick = onViewHistory) {
                        Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(20.dp), tint = Color.Gray)
                    }
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
    onConfirm: (Double, String, String) -> Unit
) {
    var newValue by remember { mutableStateOf(investment.currentVal.toBigDecimal().stripTrailingZeros().toPlainString()) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var notes by remember { mutableStateOf("") }
    
    val parsed   = newValue.toDoubleOrNull()
    val growth   = parsed?.let { it - investment.invested }
    val locale   = java.util.Locale.getDefault()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log New Valuation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(investment.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                OutlinedTextField(
                    value = newValue, onValueChange = { newValue = it },
                    label = { Text("New Market Value ($currencySymbol)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )

                app.fynlo.ui.components.DatePickerField(value = date, onValueChange = { date = it }, label = "Valuation Date")

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )

                if (growth != null) {
                    val pct = if (investment.invested > 0) (growth / investment.invested) * 100 else 0.0
                    Text(
                        "Total Growth: ${if (growth >= 0) "+" else ""}$currencySymbol ${String.format(locale, "%,.0f", growth)} (${String.format(locale, "%.1f", pct)}%)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (growth >= 0) Color(0xFF059669) else Color(0xFFEF4444)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { parsed?.let { onConfirm(it, date, notes) } }, enabled = parsed != null) {
                Text("Log Valuation")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ValuationHistoryDialog(
    investment: Investment,
    currencySymbol: String,
    valuations: List<app.fynlo.data.model.InvestmentValuation>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Valuation History") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(investment.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                
                if (valuations.isEmpty()) {
                    Text("No records found", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(valuations) { v ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(DateUtils.formatToDisplay(v.date), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    if (v.notes.isNotBlank()) {
                                        Text(v.notes, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                Text("$currencySymbol ${String.format("%,.0f", v.value)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
