package app.fynlo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLocale
import app.fynlo.data.model.Transaction
import app.fynlo.logic.OrphanTransactionsScanner.Orphan
import app.fynlo.logic.OrphanTransactionsScanner.Side
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.ui.theme.SemanticAmber
import app.fynlo.ui.theme.Emerald500
import java.util.Locale

/**
 * 3.2.59 — orphan-account repair dialog.
 *
 * Lists every orphan transaction. Each row exposes an
 * `ExposedDropdownMenuBox` of the user's real accounts; tapping "Fix"
 * calls back with the new (repaired) Transaction so the caller can run
 * `repository.editTransaction(old, new)` — which naturally
 * - reverses the old (no-op since the orphan account doesn't exist), then
 * - applies the new (delta lands on the real account this time).
 *
 * No bulk-apply — fixes happen one at a time so the user can sanity-check
 * each. Closing the dialog with unfixed orphans is fine; the banner on
 * HomeScreen will keep showing them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrphanRepairDialog(
    orphans:      List<Orphan>,
    accounts:     List<String>,
    currencyCode: String,
    onFix:        (old: Transaction, new: Transaction) -> Unit,
    onDismiss:    () -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    FormDialog(
        title     = "Fix orphan transactions",
        onDismiss = onDismiss,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.WarningAmber, null, tint = SemanticAmber, modifier = Modifier.size(20.dp))
            Text(
                "${orphans.size} transaction${if (orphans.size == 1) "" else "s"} reference accounts that don't exist. " +
                "Pick a real account for each to update your balances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        // LazyColumn inside the FormDialog scroll → set a height cap so
        // it doesn't try to fill the whole window. 360dp fits ~5 rows.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(orphans, key = { it.transaction.id + it.side.name }) { orphan ->
                OrphanRow(
                    orphan       = orphan,
                    accounts     = accounts,
                    currencyCode = currencyCode,
                    locale       = locale,
                    onFix        = onFix,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Done", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrphanRow(
    orphan:       Orphan,
    accounts:     List<String>,
    currencyCode: String,
    locale:       Locale,
    onFix:        (Transaction, Transaction) -> Unit,
) {
    val t = orphan.transaction
    var picked   by remember { mutableStateOf(accounts.firstOrNull().orEmpty()) }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text(
                        t.desc.ifBlank { t.category },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        "${t.type} • ${t.date} • typed: \"${orphan.typedName}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    CurrencyFormatter.detail(t.amount, currencyCode, locale),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = SemanticAmber,
                )
            }
            Spacer(Modifier.height(10.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value         = picked,
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Re-point to") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    modifier      = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { acct ->
                        DropdownMenuItem(
                            text    = { Text(acct) },
                            onClick = { picked = acct; expanded = false }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (picked.isNotBlank()) {
                        val new = when (orphan.side) {
                            Side.FROM -> t.copy(fromAcct = picked)
                            Side.TO   -> t.copy(toAcct   = picked)
                        }
                        onFix(t, new)
                    }
                },
                enabled  = picked.isNotBlank() && accounts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Emerald500),
            ) { Text("Fix") }
        }
    }
}
