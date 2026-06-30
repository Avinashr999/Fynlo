package app.fynlo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fynlo.ui.theme.TemplateBorder
import app.fynlo.ui.theme.Emerald700

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
    subtitle: String = "Review the details before saving",
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.86f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 0.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(0.8.dp, TemplateBorder),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.8.dp, TemplateBorder),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    modifier = Modifier.size(18.dp),
                                    tint = Emerald700,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
                    ) {
                        content()
                    }
                }
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
