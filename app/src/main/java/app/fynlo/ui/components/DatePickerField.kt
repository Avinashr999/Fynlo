package app.fynlo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Year
import java.time.format.DateTimeFormatter

/**
 * Reusable date picker field.
 * Displays a picker-style field. Tapping opens a Material3 DatePickerDialog.
 * value / onValueChange use "DD-MM-YYYY" format (app's internal format).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                focusManager.clearFocus(force = true)
                showPicker = true
            }
        }
    }

    // Parse current value to populate the picker
    val initialMillis = remember(value) {
        runCatching {
            val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val d   = LocalDate.parse(value, fmt)
            d.toEpochDay() * 86_400_000L
        }.getOrDefault(LocalDate.now().toEpochDay() * 86_400_000L)
    }

    val yearRange = remember {
        val currentYear = Year.now().value
        (currentYear - 80)..(currentYear + 50)
    }

    val state = key(initialMillis) {
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = yearRange,
        )
    }

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val d   = LocalDate.ofEpochDay(millis / 86_400_000L)
                        val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
                        onValueChange(d.format(fmt))
                    }
                    showPicker = false
                }) { Text("Use date", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (optional && value.isNotBlank()) {
                        TextButton(onClick = {
                            onValueChange("")
                            showPicker = false
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    OutlinedTextField(
        value         = value,
        onValueChange = {},
        label         = { Text(if (optional) "$label (optional)" else label) },
        trailingIcon  = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (optional && value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { showPicker = true }) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Pick date",
                        tint = app.fynlo.ui.theme.Emerald500,
                    )
                }
            }
        },
        readOnly      = true,
        singleLine    = true,
        interactionSource = interactionSource,
        modifier      = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        shape         = RoundedCornerShape(18.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor      = app.fynlo.ui.theme.Emerald500,
            unfocusedBorderColor    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            focusedLabelColor       = app.fynlo.ui.theme.Emerald500,
            unfocusedLabelColor     = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor             = app.fynlo.ui.theme.Emerald500
        ),
        placeholder   = { Text("DD-MM-YYYY") }
    )
}
