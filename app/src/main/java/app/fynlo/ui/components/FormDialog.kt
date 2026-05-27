package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * C22 dialog universalization (3.2.53) — canonical form-dialog shape.
 *
 * Lifts the layout pattern established by `AddLendingDialog` /
 * `AddTransactionDialog` / `AddDebtDialog` / `AddInvestmentDialog` /
 * `PaymentDialog` so the rest of the app's form dialogs (Goal, Budget,
 * Person, Project, Recurring, EMI Calculator, Log Valuation, Edit
 * Balance) can reach the same look in one call:
 *
 *   - `Dialog` (not `AlertDialog`) so we control the surface and width.
 *   - `Surface` at 94% width, 28-dp rounded corners, tonal elevation 4dp
 *     → the subtle green tint that distinguishes a form from a confirm.
 *   - 20-dp inner padding + `verticalScroll` so the form survives any
 *     content height (the AlertDialog text-slot clipping issue from
 *     3.2.50–3.2.51 doesn't apply here, but scroll keeps the behaviour).
 *   - `imePadding()` so the keyboard doesn't shove the bottom button
 *     off-screen when a text field is focused.
 *   - Title row with bold title + top-right X close icon (matches the
 *     Lending pattern). Cancel is the X — no bottom Cancel button.
 *
 * Form content goes inside `content`. Caller supplies the form fields,
 * then a full-width primary button + `DisabledButtonHint` at the end.
 *
 * For dialogs with a "hero number" (Lending amount, Goal target, Budget
 * limit, etc.), use [FormDialogHero] inside `content` before the fields.
 */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/**
 * C22 dialog universalization — bold section label inside a [FormDialog]
 * body. Lifts the "Borrower" / "Lend from" / "Interest type" label
 * pattern from the Lending dialog so other migrated forms use the same
 * label style instead of `OutlinedTextField` floating labels.
 *
 * Place above the input it labels; pair with `Spacer(Modifier.height(8.dp))`
 * for consistent rhythm.
 */
@Composable
fun FormSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
