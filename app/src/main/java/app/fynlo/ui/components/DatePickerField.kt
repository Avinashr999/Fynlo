package app.fynlo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Reusable date picker field.
 * Displays a text field. Tapping opens a Material3 DatePickerDialog.
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

    // Parse current value to populate the picker
    val initialMillis = remember(value) {
        runCatching {
            val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val d   = LocalDate.parse(value, fmt)
            d.toEpochDay() * 86_400_000L
        }.getOrDefault(LocalDate.now().toEpochDay() * 86_400_000L)
    }

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

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
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(if (optional) "$label (optional)" else label) },
        trailingIcon  = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
            }
        },
        readOnly      = false,   // still allow manual typing
        singleLine    = true,
        modifier      = modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(16.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            focusedBorderColor      = app.fynlo.ui.theme.Emerald500,
            unfocusedBorderColor    = androidx.compose.ui.graphics.Color.Transparent,
            focusedLabelColor       = app.fynlo.ui.theme.Emerald500,
            cursorColor             = app.fynlo.ui.theme.Emerald500
        ),
        placeholder   = { Text("DD-MM-YYYY") }
    )
}


