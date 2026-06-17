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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.pow
import app.fynlo.FinanceViewModel
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.components.DatePickerField
import app.fynlo.ui.theme.*

/**
 * EMI Calculator (renamed from "Loan Calculator" in 3.2.16 per user feedback
 * that the screen *is* an EMI calculator — that's the canonical term in the
 * Indian-finance context and the audience this app serves).
 *
 * 3.2.16 visual polish pass:
 *   - Header rename ("Loan Calculator" → "EMI Calculator").
 *   - Tenure unit segmented row no longer wraps inside the same `weight(1f)`
 *     as the number input; it now hugs its natural width with the input
 *     taking the rest of the row.
 *   - Result cards bumped from `bodySmall` (which wrapped on narrower
 *     phones) to `titleMedium` — proper visual hierarchy for the
 *     headline numbers.
 *   - Loan/Due date inputs swapped from raw text fields to `DatePickerField`
 *     (same component used everywhere else in the app).
 *   - "Outstanding as of today" section hidden behind an explicit
 *     `Already took this loan?` toggle — the primary use case is planning
 *     a *future* EMI; the outstanding-interest path is the exception, not
 *     the default. Cleans up the form for the common case.
 *   - Amortization schedule got a Month-wise / Year-wise toggle. Yearly is
 *     the default — 24 monthly rows was a wall of text most users scan past;
 *     yearly summary (one row per year with summed principal + interest +
 *     end-of-year balance) is what the user actually wants to see.
 *   - Reset button — clears all inputs and the dates. Useful when iterating
 *     between scenarios.
 *
 * Deferred to a separate cluster (per user "just visuals, skip features"):
 *   - Save as Debt (push computed loan into Debts tracker).
 *   - Prepayment simulation (modal: "prepay ₹X in month Y, see savings").
 *   - Affordability % (EMI vs declared salary).
 *   - Compare two scenarios side-by-side.
 *   - Share / export schedule (CSV or PDF).
 *   - EMI breakdown pie chart.
 *
 * Per UX_AUDIT C12-C15 P1 backlog — this commit closes the visual half.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorScreen(viewModel: FinanceViewModel? = null) {
    val locale = LocalLocale.current.platformLocale
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

    // 3.2.16 — outstanding-interest section is opt-in. Most users planning
    // an EMI haven't taken the loan yet, so showing the "days elapsed /
    // accrued interest" panel by default was noise. Now hidden until the
    // user explicitly says "I already took this loan and want to see what
    // I owe today."
    var trackExistingLoan by remember { mutableStateOf(false) }

    // 3.2.16 — Month-wise / Year-wise amortization toggle. Yearly is the
    // default; the user can switch if they want the granular view.
    var scheduleGranularity by remember { mutableStateOf("Yearly") }

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

    // 3.2.16 — reset clears every input back to defaults. The dates
    // get re-initialised to today (loan) and blank (due) so the user
    // doesn't fall into the trap of a stale loan date triggering the
    // accrued-interest panel after a reset.
    val resetAll: () -> Unit = {
        principal = ""
        rate = ""
        tenure = ""
        tenureUnit = "Months"
        intType = "Reducing Balance"
        loanDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        dueDate = ""
        trackExistingLoan = false
        scheduleGranularity = "Yearly"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "EMI Calculator",
            subtitle = "Plan your EMI before borrowing",
            action = {
                // 3.2.16 — Reset button. Tonal so it's discoverable but
                // doesn't compete with the primary input flow.
                FilledTonalIconButton(onClick = resetAll) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }
            }
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).imePadding()) {
            Spacer(Modifier.height(8.dp))

            // ── Input card ────────────────────────────────────────────────
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

                // 3.2.16 — tenure input + unit picker. The unit segmented row
                // used to share `weight(1f)` with the number input, making
                // them both half-width and the segment labels (Months / Years)
                // cramped. Now the number input is `weight(1f)` (takes
                // remaining space) and the unit picker hugs its natural
                // width — far cleaner.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = tenure, onValueChange = { tenure = it },
                        label = { Text("Tenure") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    val tenureUnits = listOf("Months", "Years")
                    TemplateSegmentedSelector(
                        options = tenureUnits,
                        selectedIndex = tenureUnits.indexOf(tenureUnit).coerceAtLeast(0),
                        onSelected = { idx -> tenureUnit = tenureUnits[idx] },
                        modifier = Modifier.width(156.dp),
                    )
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

                // 3.2.16 — Already-took-this-loan toggle. Hides the
                // outstanding-as-of-today section by default; surfaces it
                // when the user opts in. Cleaner default form, same
                // functionality available on demand.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Already took this loan?",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "Show accrued interest from loan date onward",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = trackExistingLoan,
                        onCheckedChange = { trackExistingLoan = it },
                    )
                }

                if (trackExistingLoan) {
                    HorizontalDivider()
                    DatePickerField(
                        value = loanDate,
                        onValueChange = { loanDate = it },
                        label = "Loan Date",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DatePickerField(
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = "Due Date",
                        modifier = Modifier.fillMaxWidth(),
                        optional = true,
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
            }

            Spacer(Modifier.height(20.dp))

            // ── Results section ───────────────────────────────────────────
            if (result != null) {
                // EMI headline — hero number
                Column(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Monthly EMI",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        CurrencyFormatter.detail(result.emi, currencyCode, locale),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = Emerald500,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 3.2.16 — result cards bumped from bodySmall to titleMedium.
                // Three side-by-side cards in the same row at narrow widths
                // cramped tiny numbers; titleMedium with proper card padding
                // makes the headline-level information actually readable.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultCard(
                        "Principal",
                        CurrencyFormatter.detail(principal.toDoubleOrNull() ?: 0.0, currencyCode, locale),
                        MaterialTheme.colorScheme.primary,
                        Modifier.weight(1f),
                    )
                    ResultCard(
                        "Total Interest",
                        CurrencyFormatter.detail(result.totalInterest, currencyCode, locale),
                        SemanticRed,
                        Modifier.weight(1f),
                    )
                    ResultCard(
                        "Total Payment",
                        CurrencyFormatter.detail(result.totalPayment, currencyCode, locale),
                        Emerald500,
                        Modifier.weight(1f),
                    )
                }

                val pct = if ((principal.toDoubleOrNull() ?: 0.0) > 0)
                    result.totalInterest / (principal.toDoubleOrNull() ?: 1.0) * 100 else 0.0
                Spacer(Modifier.height(12.dp))
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

                // ── Outstanding-as-of-today (only when trackExistingLoan) ─
                if (trackExistingLoan && daysElapsed > 0 && (principal.toDoubleOrNull() ?: 0.0) > 0) {
                    Spacer(Modifier.height(16.dp))
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
                        Text(
                            "Outstanding as of Today",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(
                                "Days Elapsed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "$daysElapsed days",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(
                                "Interest Accrued",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                CurrencyFormatter.detail(accrued, currencyCode, locale),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = SemanticRed,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(
                                "Per Day Interest",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                CurrencyFormatter.detail(perDay, currencyCode, locale),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                        if (isOverdue) {
                            Text(
                                "Overdue by $daysOverdue days",
                                style = MaterialTheme.typography.labelMedium,
                                color = SemanticRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SemanticRed.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            Arrangement.SpaceBetween,
                            Alignment.CenterVertically,
                        ) {
                            Text(
                                "Total Outstanding",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                CurrencyFormatter.detail(outstanding, currencyCode, locale),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = SemanticRed,
                            )
                        }
                    }
                }

                // ── Amortization schedule ────────────────────────────────
                if (result.schedule.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Amortization Schedule",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        // 3.2.16 — granularity toggle. Yearly aggregates the
                        // 12 months of each year into one row (year #,
                        // sum-of-principal, sum-of-interest, end-of-year
                        // remaining balance) so a 30-year home loan goes
                        // from a 360-row wall of text to a scannable 30 rows.
                        val granOptions = listOf("Yearly", "Monthly")
                        TemplateSegmentedSelector(
                            options = granOptions,
                            selectedIndex = granOptions.indexOf(scheduleGranularity).coerceAtLeast(0),
                            onSelected = { idx -> scheduleGranularity = granOptions[idx] },
                            modifier = Modifier.width(170.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(16.dp)
                    ) {
                        if (scheduleGranularity == "Yearly") {
                            // Year-wise aggregation: chunk the monthly
                            // schedule into 12-month groups; for each year
                            // sum the principal + interest and compute the
                            // remaining balance after the year ends.
                            val totalPrincipal = principal.toDoubleOrNull() ?: 0.0
                            val years = result.schedule.chunked(12)
                            var balance = totalPrincipal

                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                listOf("Year", "Principal", "Interest", "Balance").forEach { h ->
                                    Text(
                                        h,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            years.forEachIndexed { idx, yearChunk ->
                                val yearNum = idx + 1
                                val sumPrin = yearChunk.sumOf { it.second }
                                val sumInt = yearChunk.sumOf { it.third }
                                balance = (balance - sumPrin).coerceAtLeast(0.0)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "$yearNum",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(sumPrin, currencyCode, locale),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(sumInt, currencyCode, locale),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SemanticRed,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(balance, currencyCode, locale),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                }
                                if (idx < years.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                            }
                        } else {
                            // Monthly view — kept for users who want
                            // detailed precision. First 24 rows; deep loans
                            // truncated with a "... N more months" footer.
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                listOf("Month", "Principal", "Interest", "EMI").forEach { h ->
                                    Text(
                                        h,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            result.schedule.take(24).forEach { (month, prin, interest) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "$month",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(prin, currencyCode, locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(interest, currencyCode, locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SemanticRed,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        CurrencyFormatter.listRow(prin + interest, currencyCode, locale),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            }
                            if (result.schedule.size > 24) {
                                Text(
                                    "... ${result.schedule.size - 24} more months — switch to Yearly to see the full term",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                // 3.2.80 — Prepayment what-if + Affordability sections.
                // Both opt-in via Switch (default OFF) so the base
                // calculator flow stays focused. Reducing-Balance only for
                // prepayment — Simple/Compound don't have a meaningful
                // "shortens the term" semantic.
                if (intType == "Reducing Balance") {
                    Spacer(Modifier.height(20.dp))
                    PrepaymentWhatIfSection(
                        principal = principal.toDoubleOrNull() ?: 0.0,
                        annualRatePct = rate.toDoubleOrNull() ?: 0.0,
                        tenureMonths = tenureMonths,
                        baselineEmi = result.emi,
                        baselineTotalInterest = result.totalInterest,
                        currencyCode = currencyCode,
                        currencySymbol = currencySymbol,
                        locale = locale,
                    )
                }

                Spacer(Modifier.height(20.dp))
                AffordabilitySection(
                    monthlyEmi = result.emi,
                    currencySymbol = currencySymbol,
                )
            } else {
                // Empty state — clear guidance about what to enter.
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter principal, rate, and tenure",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Your EMI and full amortization will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }

            Spacer(Modifier.height(FabBottomPadding))
        }
    }
}

/**
 * 3.2.80 — Prepayment what-if section for the EMI Calculator.
 *
 * Lets users simulate two prepayment shapes against the current baseline:
 *   - **Monthly extra**: pay ₹X on top of the baseline EMI every month
 *   - **Lump sum**: pay ₹X extra in month N once
 *
 * Surfaces months saved + interest saved + new payoff month. Toggleable
 * (default OFF) so the base calculator flow stays focused. Mirrors the
 * "What if I pay extra?" pattern from Debt detail (3.2.64) for visual
 * consistency.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrepaymentWhatIfSection(
    principal: Double,
    annualRatePct: Double,
    tenureMonths: Int,
    baselineEmi: Double,
    baselineTotalInterest: Double,
    currencyCode: String,
    currencySymbol: String,
    locale: Locale,
) {
    var expanded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Monthly extra") }
    var extraAmount by remember { mutableStateOf("") }
    var lumpMonth by remember { mutableStateOf("12") }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("What if I prepay?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Months saved + interest saved at a faster pace",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White,
                        checkedTrackColor    = Emerald500,
                        uncheckedThumbColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor  = MaterialTheme.colorScheme.surface,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                // Mode toggle.
                val modeOptions = listOf("Monthly extra", "Lump sum")
                TemplateSegmentedSelector(
                    options = modeOptions,
                    selectedIndex = modeOptions.indexOf(mode).coerceAtLeast(0),
                    onSelected = { idx -> mode = modeOptions[idx] },
                )

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = extraAmount,
                    onValueChange = { extraAmount = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (mode == "Monthly extra") "Extra / month ($currencySymbol)" else "Lump sum ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                if (mode == "Lump sum") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lumpMonth,
                        onValueChange = { lumpMonth = it.filter(Char::isDigit) },
                        label = { Text("Pay at month (1..${tenureMonths})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                val extra = extraAmount.toDoubleOrNull() ?: 0.0
                val month = lumpMonth.toIntOrNull() ?: 1
                val simMode = if (mode == "Monthly extra")
                    app.fynlo.logic.EmiPrepaymentSimulator.Mode.MonthlyExtra(extra)
                else
                    app.fynlo.logic.EmiPrepaymentSimulator.Mode.LumpSum(extra, month.coerceAtLeast(1))
                val r = remember(principal, annualRatePct, tenureMonths, simMode) {
                    app.fynlo.logic.EmiPrepaymentSimulator.simulate(principal, annualRatePct, tenureMonths, simMode)
                }

                Spacer(Modifier.height(12.dp))
                if (extra > 0.0 && r.feasible) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                        ResultCard(
                            "New tenure",
                            "${r.totalMonths} mo",
                            Emerald500,
                            Modifier.weight(1f),
                        )
                        ResultCard(
                            "Months saved",
                            "${r.monthsSaved}",
                            if (r.monthsSaved > 0) Emerald500 else MaterialTheme.colorScheme.outlineVariant,
                            Modifier.weight(1f),
                        )
                        ResultCard(
                            "Interest saved",
                            CurrencyFormatter.detail(r.interestSaved, currencyCode, locale),
                            if (r.interestSaved > 1.0) Emerald500 else MaterialTheme.colorScheme.outlineVariant,
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val newPayoff = java.time.LocalDate.now().plusMonths(r.totalMonths.toLong())
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", locale))
                    Text(
                        "Loan clears by $newPayoff — ${r.monthsSaved} months sooner.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (extra <= 0.0) {
                    Text(
                        "Enter a positive amount to see how prepayments shorten your loan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/**
 * 3.2.80 — affordability check. Input net monthly income, see what % of
 * income the EMI consumes and whether that's safe.
 */
@Composable
private fun AffordabilitySection(
    monthlyEmi: Double,
    currencySymbol: String,
) {
    val locale = LocalLocale.current.platformLocale
    var expanded by remember { mutableStateOf(false) }
    var incomeText by remember { mutableStateOf("") }

    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Can I afford this EMI?",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Text("Compare against your monthly income",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White,
                        checkedTrackColor    = Emerald500,
                        uncheckedThumbColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor  = MaterialTheme.colorScheme.surface,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = incomeText,
                    onValueChange = { incomeText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Net monthly income ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                val income = incomeText.toDoubleOrNull() ?: 0.0
                val assessment = remember(monthlyEmi, income) {
                    app.fynlo.logic.EmiAffordability.assess(monthlyEmi, income)
                }
                val tintColor = when (assessment.verdict) {
                    app.fynlo.logic.EmiAffordability.Verdict.COMFORTABLE -> Emerald500
                    app.fynlo.logic.EmiAffordability.Verdict.MANAGEABLE  -> SemanticBlue
                    app.fynlo.logic.EmiAffordability.Verdict.STRETCHED   -> SemanticAmber
                    app.fynlo.logic.EmiAffordability.Verdict.RISKY       -> SemanticRed
                    else                                                 -> MaterialTheme.colorScheme.outlineVariant
                }

                Spacer(Modifier.height(12.dp))
                if (assessment.verdict != app.fynlo.logic.EmiAffordability.Verdict.INVALID_INPUT) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("EMI burden",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${String.format(locale, "%.1f", assessment.burdenPct)}% of income",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = tintColor,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = tintColor.copy(alpha = 0.15f),
                        ) {
                            Text(
                                assessment.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = tintColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        assessment.explanation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        assessment.explanation,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun ResultCard(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.08f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        // 3.2.16 — was `bodySmall.copy(fontWeight = Bold)` which was 14sp.
        // Three of these in a Row at narrow widths made the numbers crash
        // into each other. titleMedium (16sp) keeps the cards readable at
        // typical phone widths while still fitting comfortably in 3-up
        // layout.
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
