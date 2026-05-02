package com.example.cashmemo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorScreen() {
    val locale = remember { Locale.getDefault() }

    var principal  by remember { mutableStateOf("") }
    var rate       by remember { mutableStateOf("") }
    var tenure     by remember { mutableStateOf("") }
    var tenureUnit by remember { mutableStateOf("Months") } // Months / Years
    var intType    by remember { mutableStateOf("Reducing Balance") }
    var expanded   by remember { mutableStateOf(false) }

    val intTypes = listOf("Reducing Balance", "Simple Interest", "Compound Interest")

    val tenureMonths = remember(tenure, tenureUnit) {
        val t = tenure.toIntOrNull() ?: 0
        if (tenureUnit == "Years") t * 12 else t
    }

    // Results
    data class CalcResult(
        val emi: Double,
        val totalPayment: Double,
        val totalInterest: Double,
        val schedule: List<Triple<Int, Double, Double>> // month, principal, interest
    )

    val result = remember(principal, rate, tenureMonths, intType) {
        val p = principal.toDoubleOrNull() ?: return@remember null
        val r = rate.toDoubleOrNull() ?: return@remember null
        val n = tenureMonths
        if (p <= 0 || r <= 0 || n <= 0) return@remember null

        when (intType) {
            "Reducing Balance" -> {
                val rMonthly = r / 100.0 / 12.0
                val emi = if (rMonthly == 0.0) p / n
                          else p * rMonthly * (1 + rMonthly).pow(n) / ((1 + rMonthly).pow(n) - 1)
                val schedule = mutableListOf<Triple<Int, Double, Double>>()
                var balance = p
                repeat(n) { month ->
                    val intPart = balance * rMonthly
                    val prinPart = (emi - intPart).coerceAtLeast(0.0)
                    balance -= prinPart
                    schedule.add(Triple(month + 1, prinPart, intPart))
                }
                CalcResult(emi, emi * n, emi * n - p, schedule)
            }
            "Simple Interest" -> {
                val totalInterest = p * r / 100.0 * (n / 12.0)
                val total = p + totalInterest
                val emi   = total / n
                CalcResult(emi, total, totalInterest, emptyList())
            }
            "Compound Interest" -> {
                val total = p * (1 + r / 100.0).pow(n / 12.0)
                val emi   = total / n
                CalcResult(emi, total, total - p, emptyList())
            }
            else -> null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Loan Calculator",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(vertical = 16.dp))
        Text("Plan your loan before taking it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(16.dp))

        // Inputs
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                OutlinedTextField(
                    value = principal, onValueChange = { principal = it },
                    label = { Text("Principal Amount (₹)") },
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
                )

                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text("Annual Interest Rate (%)") },
                    leadingIcon = { Icon(Icons.Default.Percent, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tenure, onValueChange = { tenure = it },
                        label = { Text("Tenure") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Months", "Years").forEach { unit ->
                            FilterChip(
                                selected = tenureUnit == unit,
                                onClick  = { tenureUnit = unit },
                                label    = { Text(unit) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Interest type dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = intType, onValueChange = {}, readOnly = true,
                        label = { Text("Interest Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        intTypes.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { intType = t; expanded = false })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Results
        if (result != null) {
            // EMI card
            Card(
                Modifier.fillMaxWidth(), RoundedCornerShape(20.dp),
                CardDefaults.cardColors(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Monthly EMI", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹ ${String.format(locale, "%,.2f", result.emi)}",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Breakdown
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultCard("Principal", "₹${String.format(locale, "%,.0f", principal.toDoubleOrNull() ?: 0.0)}",
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ResultCard("Total Interest", "₹${String.format(locale, "%,.0f", result.totalInterest)}",
                    Color(0xFFEF4444), Modifier.weight(1f))
                ResultCard("Total Payment", "₹${String.format(locale, "%,.0f", result.totalPayment)}",
                    Color(0xFF10B981), Modifier.weight(1f))
            }

            // Interest % of principal
            val pct = if ((principal.toDoubleOrNull() ?: 0.0) > 0)
                result.totalInterest / (principal.toDoubleOrNull() ?: 1.0) * 100 else 0.0
            Spacer(Modifier.height(8.dp))
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("Interest is ${String.format(locale, "%.1f", pct)}% of principal",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Method: $intType", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Amortization schedule (reducing balance only)
            if (result.schedule.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Amortization Schedule",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            listOf("Month", "Principal", "Interest", "Total EMI").forEach { h ->
                                Text(h, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        result.schedule.take(24).forEach { (month, prin, interest) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
                                Text("$month", style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("₹${String.format(locale, "%,.0f", prin)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("₹${String.format(locale, "%,.0f", interest)}",
                                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444),
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("₹${String.format(locale, "%,.0f", prin + interest)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                        if (result.schedule.size > 24) {
                            Text("... ${result.schedule.size - 24} more months",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun ResultCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, RoundedCornerShape(12.dp), CardDefaults.cardColors(color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = color, textAlign = TextAlign.Center)
        }
    }
}
