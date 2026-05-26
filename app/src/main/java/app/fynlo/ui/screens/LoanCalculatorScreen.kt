package app.fynlo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorScreen(viewModel: FinanceViewModel? = null) {
    val locale = remember { Locale.getDefault() }
    val currentProjectState = viewModel?.currentProject?.collectAsState()
    val currentProject = currentProjectState?.value
    val currencyCode = currentProject?.currency ?: "INR"
    val currencySymbol = app.fynlo.logic.CurrencyUtils.symbolFor(currencyCode)

    var principal  by remember { mutableStateOf("") }
    var rate       by remember { mutableStateOf("") }
    var tenure     by remember { mutableStateOf("") }
    var tenureUnit by remember { mutableStateOf("Months") }
    var intType    by remember { mutableStateOf("Reducing Balance") }
    var loanDate   by remember { mutableStateOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))) }
    var dueDate    by remember { mutableStateOf("") }
    var expanded   by remember { mutableStateOf(false) }

    val intTypes = listOf("Reducing Balance", "Simple Interest", "Compound Interest")

    val daysElapsed = remember(loanDate) {
        runCatching {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(loanDate, fmt), java.time.LocalDate.now()
            ).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }
    val isOverdue = remember(dueDate) {
        if (dueDate.isBlank()) false
        else runCatching {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            java.time.LocalDate.parse(dueDate, fmt).isBefore(java.time.LocalDate.now())
        }.getOrDefault(false)
    }
    val daysOverdue = remember(dueDate) {
        if (dueDate.isBlank()) 0
        else runCatching {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(dueDate, fmt), java.time.LocalDate.now()
            ).toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }

    val tenureMonths = remember(tenure, tenureUnit) {
        val t = tenure.toIntOrNull() ?: 0
        if (tenureUnit == "Years") t * 12 else t
    }

    data class CalcResult(
        val emi: Double,
        val totalPayment: Double,
        val totalInterest: Double,
        val schedule: List<Triple<Int, Double, Double>>
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

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Loan Calculator", "Plan your EMI before borrowing")
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding()) {
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

                OutlinedTextField(
                    value = principal, onValueChange = { principal = it },
                    label = { Text("Principal Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
                )

                OutlinedTextField(
                    value = rate, onValueChange = { rate = it },
                    label = { Text("Annual Interest Rate (%)") },
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
                    // 3.2.11 chip-sweep: tenure unit was a vertical Column of 2 fillMaxWidth chips
                    // (awkward stacked layout) — now a horizontal SegmentedButtonRow that sits
                    // beside the Tenure number input. `icon = {}` per the 3.2.8 lesson.
                    val tenureUnits = listOf("Months", "Years")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        tenureUnits.forEachIndexed { idx, unit ->
                            SegmentedButton(
                                selected = tenureUnit == unit,
                                onClick = { tenureUnit = unit },
                                shape = SegmentedButtonDefaults.itemShape(idx, tenureUnits.size),
                                icon = {},
                                label = { Text(unit, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }

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

                HorizontalDivider()
                Text("Loan Dates (for outstanding calculation)",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = loanDate, onValueChange = { loanDate = it },
                    label = { Text("Loan Date (DD-MM-YYYY)") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
                OutlinedTextField(
                    value = dueDate, onValueChange = { dueDate = it },
                    label = { Text("Due Date (DD-MM-YYYY) — optional") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true
                )
                if (daysElapsed > 0) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(10.dp),
                        Arrangement.SpaceBetween
                    ) {
                        Text("Days elapsed: $daysElapsed", style = MaterialTheme.typography.bodySmall)
                        if (isOverdue) Text("Overdue: $daysOverdue days",
                            style = MaterialTheme.typography.bodySmall,
                            color = SemanticRed)
                    }
                }
        }

        Spacer(Modifier.height(16.dp))

        if (result != null) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Monthly EMI", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CurrencyFormatter.detail(result.emi, currencyCode, locale),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = Emerald500)
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultCard("Principal", CurrencyFormatter.detail(principal.toDoubleOrNull() ?: 0.0, currencyCode, locale),
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ResultCard("Total Interest", CurrencyFormatter.detail(result.totalInterest, currencyCode, locale),
                    SemanticRed, Modifier.weight(1f))
                ResultCard("Total Payment", CurrencyFormatter.detail(result.totalPayment, currencyCode, locale),
                    Emerald500, Modifier.weight(1f))
            }

            val pct = if ((principal.toDoubleOrNull() ?: 0.0) > 0)
                result.totalInterest / (principal.toDoubleOrNull() ?: 1.0) * 100 else 0.0
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(12.dp),
                Arrangement.SpaceBetween
            ) {
                Text("Interest is ${String.format(locale, "%.1f", pct)}% of principal",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Method: $intType", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (daysElapsed > 0 && (principal.toDoubleOrNull() ?: 0.0) > 0) {
                Spacer(Modifier.height(12.dp))
                val loanDateFmt = runCatching {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                    val d = java.time.LocalDate.parse(loanDate, fmt)
                    d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                }.getOrDefault("")
                val dueDateFmt = runCatching {
                    if (dueDate.isBlank()) ""
                    else {
                        val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        val d = java.time.LocalDate.parse(dueDate, fmt)
                        d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    }
                }.getOrDefault("")
                val p = principal.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                val accrued = app.fynlo.logic.InterestEngine.calcIntAccrued(
                    amount = p, rate = r, loanDate = loanDateFmt,
                    intType = intType, dueDate = dueDateFmt
                )
                val outstanding = p + accrued
                val perDay = if (daysElapsed > 0) accrued / daysElapsed else 0.0

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(SemanticRed.copy(alpha = 0.08f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        Text("Outstanding as of Today",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Days Elapsed", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$daysElapsed days", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Interest Accrued", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.detail(accrued, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SemanticRed)
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text("Per Day Interest", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.detail(perDay, currencyCode, locale),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                        if (isOverdue) {
                            Text("Overdue by $daysOverdue days",
                                style = MaterialTheme.typography.labelSmall,
                                color = SemanticRed,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .background(SemanticRed.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Text("Total Outstanding", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(CurrencyFormatter.detail(outstanding, currencyCode, locale),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = SemanticRed)
                        }
                }
            }

            if (result.schedule.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Amortization Schedule",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(16.dp)
                ) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            listOf("Month", "Principal", "Interest", "Total EMI").forEach { h ->
                                Text(h, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        result.schedule.take(24).forEach { (month, prin, interest) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), Arrangement.SpaceBetween) {
                                Text("$month", style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text(CurrencyFormatter.detail(prin, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text(CurrencyFormatter.detail(interest, currencyCode, locale),
                                    style = MaterialTheme.typography.bodySmall, color = SemanticRed,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text(CurrencyFormatter.detail(prin + interest, currencyCode, locale),
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

        Spacer(Modifier.height(100.dp))
    }
    }
}

@Composable
fun ResultCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.08f)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = color, textAlign = TextAlign.Center)
    }
}
