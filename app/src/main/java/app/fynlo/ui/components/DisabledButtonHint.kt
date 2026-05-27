package app.fynlo.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * C17 (3.2.42) — small explanatory label rendered immediately below a
 * disabled primary action button, telling the user what input is missing.
 *
 * Per UX_AUDIT §C17 ("Save" / "Add" / "Pay ₹0" / "Confirm" buttons disabled
 * with no explanation — user stares at greyed button, doesn't know what's
 * missing). The hint takes a `reason: String?` and:
 *  - renders the reason in 11sp `onSurfaceVariant` centered text when present
 *  - renders a small bottom spacer instead when null (so the button column
 *    height stays stable when the user fills the missing field and the
 *    hint disappears — no layout jump on the primary action).
 *
 * Idiomatic usage at the call site:
 * ```
 * val disabledReason: String? = when {
 *     name.isBlank()        -> "Enter a name to continue"
 *     amount.toDoubleOrNull()?.let { it > 0 } != true -> "Enter an amount to continue"
 *     else                  -> null
 * }
 * Button(
 *     onClick = { ... },
 *     enabled = disabledReason == null,
 * ) { Text("Save") }
 * DisabledButtonHint(disabledReason)
 * ```
 */
@Composable
fun DisabledButtonHint(reason: String?, modifier: Modifier = Modifier) {
    if (reason != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        // No-reason branch reserves the same minimum height so the button
        // column doesn't shift up when the user completes the form. 14dp
        // ≈ 1 line of labelSmall + 4dp spacer above.
        Spacer(Modifier.height(14.dp))
    }
}
