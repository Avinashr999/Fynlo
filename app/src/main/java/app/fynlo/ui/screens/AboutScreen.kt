package app.fynlo.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.MenuBook
import app.fynlo.BuildConfig
import app.fynlo.ui.components.FynloBrandMark
import app.fynlo.ui.theme.*

@Composable
fun AboutScreen() {
    val green = Emerald500

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        PremiumScreenHeader("About Fynlo", "Ledger-first finance, built for clarity")
        Spacer(Modifier.height(16.dp))

        // ── App identity card ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(green.copy(alpha = 0.08f))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                FynloBrandMark(size = 56.dp)
                Column {
                    Text("Fynlo",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    Text("Personal finance ledger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("v${BuildConfig.VERSION_NAME}",
                        style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color    = green,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                            .background(green.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

        Spacer(Modifier.height(20.dp))

        // ── Feature highlights ───────────────────────────────────────────────
        Text("What Fynlo Does",
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 10.dp))

        val features = listOf(
            Icons.Default.AccountBalance    to "Track accounts, cash, and bank balances",
            Icons.Default.Group             to "Manage loans to friends and family",
            Icons.Default.CreditCard        to "Track debts you owe to others",
            Icons.AutoMirrored.Filled.TrendingUp to "Monitor investments and growth",
            Icons.Default.PieChart          to "Set budgets and track spending",
            Icons.Default.Assessment        to "Reports: P&L, net worth, cash flow",
            Icons.Default.CloudDone         to "Auto-sync to Google Firestore",
            Icons.Default.Lock              to "PIN + biometric app lock"
        )

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                features.forEach { (icon, desc) ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), tint = green)
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

        Spacer(Modifier.height(20.dp))

        // ── Privacy card ─────────────────────────────────────────────────────
        AboutInfoCard(
            icon  = Icons.Default.Shield,
            title = "Privacy & Security",
            body  = "Your data stays on your device and your personal Google Firestore. We do not collect, sell, or share your financial information with any third parties.",
            color = SemanticBlue
        )

        Spacer(Modifier.height(12.dp))

        // ── Legal disclaimer ─────────────────────────────────────────────────
        AboutInfoCard(
            icon  = Icons.Default.Gavel,
            title = "Legal Disclaimer",
            body  = "Fynlo is a manual-entry personal finance ledger for personal use only. It is NOT a banking, lending, or investment advisory service. Always verify your data with official bank statements. The developer is not responsible for any financial decisions made using this app.",
            color = SemanticRed
        )

        Spacer(Modifier.height(20.dp))

        // ── Resources (C22 Stage 1 — UX_AUDIT §C22 about items #254/#256/#257) ─
        // External links rendered as a single surface with rows. Each row
        // opens its target in the user's browser via ACTION_VIEW.
        Text(
            "Resources",
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 10.dp)
        )
        val context = LocalContext.current
        fun openUrl(url: String) {
            // Best-effort URL open; if no browser is installed (very rare)
            // the intent silently no-ops rather than crashing.
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AboutLinkRow(
                icon  = Icons.Default.Shield,
                label = "Privacy Policy",
                onClick = { openUrl("https://github.com/Avinashr999/Fynlo/blob/master/PRIVACY_POLICY.md") }
            )
            HorizontalDivider(thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            AboutLinkRow(
                icon  = Icons.AutoMirrored.Filled.MenuBook,
                label = "Open Source Licenses",
                onClick = { openUrl("https://github.com/Avinashr999/Fynlo/blob/master/LICENSES.md") }
            )
            HorizontalDivider(thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            AboutLinkRow(
                icon  = Icons.Default.History,
                label = "Changelog",
                onClick = { openUrl("https://github.com/Avinashr999/Fynlo/blob/master/CHANGELOG.md") }
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Footer ───────────────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Default.Favorite, null, Modifier.size(20.dp), tint = SemanticRed)
            Text("Made with care for personal finance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Build ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * C22 Stage 1 — single-line link row used in the new Resources section.
 * Mirrors the SettingsActionRow shape for consistency: leading icon +
 * label + trailing "open-in-new" chevron. Whole row tappable.
 */
@Composable
private fun AboutLinkRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        // C22 Stage 1 follow-up — was outlineVariant (intended for hairline
        // dividers, ~3.5:1 contrast) and barely visible at 16dp on the
        // row's surfaceVariant background. Bumped to onSurfaceVariant
        // which is the standard "secondary icon/text" tone (higher contrast).
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open in browser",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutInfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    color: Color
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
            Text(title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = color)
        }
        Text(body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
