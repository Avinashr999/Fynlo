package app.fynlo.ui.screens

import app.fynlo.ui.theme.Emerald500
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fynlo.FinanceViewModel
import app.fynlo.data.model.Borrower
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.DateUtils
import app.fynlo.logic.InterestEngine
import app.fynlo.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Data ──────────────────────────────────────────────────────────────────────

private enum class DueStatus { OVERDUE, TODAY, UPCOMING_SOON, UPCOMING, SETTLED }

private data class DueEntry(
    val borrower: Borrower,
    val dueDate: LocalDate,
    val outstanding: Double,
    val status: DueStatus
)

private fun dueStatus(due: LocalDate, today: LocalDate, outstanding: Double): DueStatus = when {
    outstanding <= 0          -> DueStatus.SETTLED
    due.isBefore(today)       -> DueStatus.OVERDUE
    due.isEqual(today)        -> DueStatus.TODAY
    due.isBefore(today.plusDays(7)) -> DueStatus.UPCOMING_SOON
    else                      -> DueStatus.UPCOMING
}

@Composable
private fun statusColor(s: DueStatus): Color = when (s) {
    DueStatus.OVERDUE        -> SemanticRed
    DueStatus.TODAY          -> MaterialTheme.colorScheme.primary   // emerald
    DueStatus.UPCOMING_SOON  -> SemanticAmber
    DueStatus.UPCOMING       -> SemanticBlue
    DueStatus.SETTLED        -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionCalendarScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToBorrower: (String) -> Unit = {}
) {
    val borrowers by viewModel.borrowers.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currencyCode = currentProject?.currency ?: "INR"
    val today = remember { LocalDate.now() }
    val locale = Locale.getDefault()
    val dbFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    var displayMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(today) }

    // Build due entries for ALL active borrowers that have a due date
    val allEntries = remember(borrowers) {
        borrowers
            .filter { it.due.isNotBlank() && it.paid < it.amount }
            .mapNotNull { b ->
                try {
                    val due = LocalDate.parse(b.due, dbFmt)
                    val interest = InterestEngine.calcIntAccrued(b.amount, b.rate, b.date, b.type, b.due, b.paid)
                    val outstanding = InterestEngine.calcOutstanding(b.amount, interest, b.paid)
                    DueEntry(b, due, outstanding, dueStatus(due, today, outstanding))
                } catch (e: Exception) { null }
            }
    }

    // Group by date for the calendar
    val entriesByDate = remember(allEntries) {
        allEntries.groupBy { it.dueDate }
    }

    // Entries for the selected date
    val selectedEntries = remember(selectedDate, allEntries) {
        if (selectedDate == null) allEntries.sortedBy { it.dueDate }
        else allEntries.filter { it.dueDate == selectedDate }
    }

    // Summary counts
    val overdueCount  = allEntries.count { it.status == DueStatus.OVERDUE }
    val todayCount    = allEntries.count { it.status == DueStatus.TODAY }
    val upcomingCount = allEntries.count { it.status == DueStatus.UPCOMING || it.status == DueStatus.UPCOMING_SOON }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader(
            title = "Collection Calendar",
            subtitle = "Due dates & overdue loans",
            action = {
                // 3.2.12: was `IconButton + tint = Color.White` against the
                // plain surface background of `PremiumScreenHeader` — invisible
                // in light mode (same bug pattern as the RecurringScreen header
                // `+` fixed in 3.2.7). `FilledTonalIconButton` paints a
                // theme-aware secondary container behind a properly-tinted
                // icon, so it stays legible in both light and dark themes.
                FilledTonalIconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        val padding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── Summary chips ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryChip("$overdueCount Overdue",  SemanticRed,     Modifier.weight(1f)) {
                        selectedDate = null
                    }
                    SummaryChip("$todayCount Today",      MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                        selectedDate = today
                    }
                    SummaryChip("$upcomingCount Upcoming", SemanticAmber,  Modifier.weight(1f)) {
                        selectedDate = null
                    }
                }
            }

            // ── Month header + navigation ────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, "Previous month")
                    }
                    Text(
                        displayMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, "Next month")
                    }
                }
            }

            // ── Day-of-week headers ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                        Text(
                            day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Calendar grid ───────────────────────────────────────────────
            item {
                CalendarGrid(
                    month = displayMonth,
                    today = today,
                    selectedDate = selectedDate,
                    entriesByDate = entriesByDate,
                    onSelectDate = { date ->
                        selectedDate = if (selectedDate == date) null else date
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Legend ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendItem("Overdue",       SemanticRed)
                    LegendItem("Today",         MaterialTheme.colorScheme.primary)
                    LegendItem("This week",     SemanticAmber)
                    LegendItem("Upcoming",      SemanticBlue)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // ── Selected date / all overdue header ──────────────────────────
            item {
                val headerText = when {
                    selectedDate == null && overdueCount > 0 -> "All overdue & upcoming"
                    selectedDate == null -> "All upcoming dues"
                    selectedDate == today -> "Today's collections"
                    else -> "Due on ${DateUtils.formatToDisplay(selectedDate!!.format(dbFmt))}"
                }
                Text(
                    headerText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ── Borrower list for selected date ─────────────────────────────
            if (selectedEntries.isEmpty()) {
                item {
                    Text(
                        "No dues on this date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                items(selectedEntries, key = { it.borrower.id }) { entry ->
                    DueEntryCard(
                        entry = entry,
                        currencyCode = currencyCode,
                        today = today,
                        locale = locale,
                        onClick = { onNavigateToBorrower(entry.borrower.id) }
                    )
                }
            }
        }
    }
}

// ── Calendar grid ─────────────────────────────────────────────────────────────

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate?,
    entriesByDate: Map<LocalDate, List<DueEntry>>,
    onSelectDate: (LocalDate) -> Unit
) {
    val firstDay  = month.atDay(1)
    val lastDay   = month.atEndOfMonth()
    // Sunday = 0 offset
    val startOffset = (firstDay.dayOfWeek.value % 7)
    val totalCells  = startOffset + lastDay.dayOfMonth
    val rows        = (totalCells + 6) / 7

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNum    = cellIndex - startOffset + 1
                    if (dayNum < 1 || dayNum > lastDay.dayOfMonth) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date     = month.atDay(dayNum)
                        val entries  = entriesByDate[date] ?: emptyList()
                        val isToday  = date == today
                        val isSelected = date == selectedDate
                        val topStatus = entries
                            .filter { it.status != DueStatus.SETTLED }
                            .minByOrNull { it.status.ordinal }?.status

                        CalendarDay(
                            day        = dayNum,
                            isToday    = isToday,
                            isSelected = isSelected,
                            dotColor   = topStatus?.let { s ->
                                @Composable { statusColorValue(s) }
                            },
                            entryCount = entries.filter { it.status != DueStatus.SETTLED }.size,
                            onClick    = { onSelectDate(date) },
                            modifier   = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun statusColorValue(s: DueStatus): Color = when (s) {
    DueStatus.OVERDUE        -> SemanticRed
    DueStatus.TODAY          -> MaterialTheme.colorScheme.primary
    DueStatus.UPCOMING_SOON  -> SemanticAmber
    DueStatus.UPCOMING       -> SemanticBlue
    DueStatus.SETTLED        -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
}

@Composable
private fun CalendarDay(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    dotColor: (@Composable () -> Color)?,
    entryCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                else if (isToday) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            if (dotColor != null && entryCount > 0) {
                val dc = dotColor()
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.height(6.dp)
                ) {
                    // Show up to 3 dots
                    repeat(minOf(entryCount, 3)) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(dc)
                        )
                        if (it < minOf(entryCount, 3) - 1) Spacer(Modifier.width(2.dp))
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

// ── Due entry card ────────────────────────────────────────────────────────────

@Composable
private fun DueEntryCard(
    entry: DueEntry,
    currencyCode: String,
    today: LocalDate,
    locale: Locale,
    onClick: () -> Unit
) {
    val color = statusColorValue(entry.status)
    val dbFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val statusLabel = when (entry.status) {
        DueStatus.OVERDUE       -> {
            val days = java.time.temporal.ChronoUnit.DAYS.between(entry.dueDate, today)
            "$days day${if (days != 1L) "s" else ""} overdue"
        }
        DueStatus.TODAY         -> "Due today"
        DueStatus.UPCOMING_SOON -> {
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, entry.dueDate)
            "Due in $days day${if (days != 1L) "s" else ""}"
        }
        DueStatus.UPCOMING      -> "Due ${DateUtils.formatToDisplay(entry.dueDate.format(dbFmt))}"
        DueStatus.SETTLED       -> "Settled"
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.borrower.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = color
                )
                if (entry.borrower.phone.isNotBlank()) {
                    Text(
                        entry.borrower.phone,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    CurrencyFormatter.detail(entry.outstanding, currencyCode, locale),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                )
                Text(
                    "outstanding",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    }
}

// ── Small components ──────────────────────────────────────────────────────────

@Composable
private fun SummaryChip(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier  = modifier.clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        color     = color.copy(alpha = 0.08f),
        border    = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        tonalElevation = 0.dp
    ) {
        Box(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style     = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color      = color
                )
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
