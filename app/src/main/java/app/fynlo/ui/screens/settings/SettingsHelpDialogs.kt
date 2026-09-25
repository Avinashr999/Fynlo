package app.fynlo.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.ui.components.FynloConfirmDialog
import app.fynlo.ui.components.FormDialog
import app.fynlo.ui.theme.ThemeController
import app.fynlo.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Green = Emerald500
private val Blue  = SemanticBlue
private val Red   = SemanticRed
private val Amber = SemanticAmber


@Composable
internal fun WhatsNewDialog(onDismiss: () -> Unit) {
    app.fynlo.ui.components.FormDialog(
        title = "What's new",
        onDismiss = onDismiss,
    ) {
        SettingsHelpBlock(
            icon = Icons.Default.AccountBalanceWallet,
            title = "Money actions now show their impact",
            body = "Income, expenses, loans, debts, investments, waivers, and transfers show where money moves before you save.",
        )
        SettingsHelpBlock(
            icon = Icons.Default.Verified,
            title = "Book check keeps totals trustworthy",
            body = "Open Book check when you want to review missing money paths, old entries, or balance warnings before exporting reports.",
        )
        SettingsHelpBlock(
            icon = Icons.Default.SwapHoriz,
            title = "Transfers are account-to-account",
            body = "Transfers should reduce one account and add the same amount to another. Net worth stays unchanged.",
        )
        SettingsHelpBlock(
            icon = Icons.Default.Restore,
            title = "Backups are safer",
            body = "Use Export JSON Backup before large edits. Restore previews counts and warnings before replacing data.",
        )
        SettingsHelpBlock(
            icon = Icons.Default.Search,
            title = "Use history when something looks wrong",
            body = "Search transactions by account, person, or category, then open details to confirm source, destination, and balance impact.",
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Got it", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
internal fun SettingsHelpBlock(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Emerald500.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = Emerald700)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ReleaseChecklistDialog(onDismiss: () -> Unit) {
    app.fynlo.ui.components.FormDialog(
        title = "Release Checklist",
        onDismiss = onDismiss,
    ) {
        ReleaseChecklistItem("Run prod debug compile", ":app:compileProdDebugKotlin")
        ReleaseChecklistItem("Run prod debug unit tests", ":app:testProdDebugUnitTest")
        ReleaseChecklistItem("Install prod and dev builds on phone", "Confirm app names, icon, login, offline mode, and Settings")
        ReleaseChecklistItem("Smoke money actions", "Income, expense, transfer, lend, collect, debt, debt payment, invest, withdraw, delete/restore")
        ReleaseChecklistItem("Check Book Check", "No serious issues before building the Play bundle")
        ReleaseChecklistItem("Export reports", "Open PDF/CSV once and check layout/readability")
        ReleaseChecklistItem("Build AAB", "Use a new versionCode and versionName before Play upload")
        ReleaseChecklistItem("Play Console", "Release notes with language tags, screenshots, feature graphic, privacy/data safety")
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text("Done", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
internal fun ReleaseChecklistItem(title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(18.dp).padding(top = 2.dp),
            tint = Emerald500,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

