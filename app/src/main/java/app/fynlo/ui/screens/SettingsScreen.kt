package app.fynlo.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
import app.fynlo.ui.theme.ThemeController
import app.fynlo.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Green = Emerald500
private val Blue  = SemanticBlue
private val Red   = SemanticRed
private val Amber = SemanticAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToUpgrade: () -> Unit = {}
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current
    val isPro by app.fynlo.billing.BillingManager.isPro.collectAsState()
    // ── Setup-wizard editable prefs (DataStore-backed) ─────────────────────
    val displayNameFlow    by UserPreferences.userDisplayName(context).collectAsState(initial = "")
    var displayName        by remember { mutableStateOf("") }
    val notifsEnabled      by UserPreferences.notificationsEnabled(context).collectAsState(initial = true)
    val defaultCurrency    by UserPreferences.defaultCurrency(context).collectAsState(initial = "INR")
    val dateFormat         by UserPreferences.dateFormat(context).collectAsState(initial = "dd-MM-yyyy")

    // Sync display name from flow on first load
    LaunchedEffect(displayNameFlow) { if (displayName.isEmpty()) displayName = displayNameFlow }


    // â"€â"€ Export launchers â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { scope.launch {
        val json = viewModel.exportAllData()
        context.contentResolver.openOutputStream(it)?.use { os -> os.write(json.toByteArray()) }
    }}}

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { scope.launch {
        context.contentResolver.openInputStream(it)?.use { ins ->
            viewModel.restoreData(ins.bufferedReader().readText())
        }
    }}}

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let {
        context.contentResolver.openOutputStream(it)?.use { os -> viewModel.exportToPDF(os) }
    }}

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(it)?.use { os ->
                app.fynlo.logic.ExcelExportUtility.generateFullBackup(
                    os,
                    viewModel.accounts.value, viewModel.transactions.value,
                    viewModel.borrowers.value, viewModel.debts.value,
                    viewModel.investments.value, viewModel.payments.value,
                    viewModel.debtPayments.value
                )
                app.fynlo.data.Analytics.dataExported("xlsx")
            }
        }
    }}}

    // ── Danger Zone — Reset All Data flow state ───────────────────────────────
    var showResetPinGate by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    var isResetting      by remember { mutableStateOf(false) }

    // PIN/biometric gate (same full-screen check as the lock screen) shown before
    // the destructive warning. Returns early so it fills the screen regardless of
    // how far the settings list is scrolled.
    if (showResetPinGate) {
        PinScreen(
            mode      = PinMode.ENTER,
            onSuccess = { showResetPinGate = false; showResetWarning = true },
            onSkip    = { showResetPinGate = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Settings", "App preferences & data management")
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // ── Upgrade to Pro (hidden until billing is enabled) ──────────────────
        if (app.fynlo.billing.FeatureFlags.BILLING_ENABLED) {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Emerald500.copy(alpha = 0.10f))
                    .clickable { onNavigateToUpgrade() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Emerald500.copy(alpha = 0.18f)),
                    Alignment.Center) {
                    Icon(Icons.Default.Star, null, Modifier.size(22.dp), tint = Emerald500)
                }
                Column(Modifier.weight(1f)) {
                    Text("Upgrade to Fynlo Pro",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Emerald500)
                    Text("Unlimited everything, cloud sync, reports & more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(16.dp),
                    tint = Emerald500)
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Appearance ────────────────────────────────────────────────────────
        SettingsSectionLabel("Personalization")
        SettingsCard {
            // Theme
            Text("Theme", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    null  to "System",
                    false to "Light",
                    true  to "Dark"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = ThemeController.darkModeOverride == value,
                        onClick  = { ThemeController.darkModeOverride = value; ThemeController.save(context) },
                        label    = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Your Name") },
                placeholder   = { Text("Optional — used for greetings") },
                singleLine    = true,
                trailingIcon  = if (displayName.isNotBlank()) {
                    { IconButton(onClick = {
                        scope.launch { UserPreferences.setUserDisplayName(context, displayName.trim()) }
                    }) { Icon(Icons.Default.Check, "Save") } }
                } else null,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Emerald500,
                    focusedLabelColor    = Emerald500
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        // â"€â"€ Cloud Backup â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        // ── Backup & Export ──────────────────────────────────────────────────
        SettingsSectionLabel("Backup & Export")
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Compact auto-backup status
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.CloudDone, null, Modifier.size(18.dp), tint = Green)
                    Text("Auto-backup on · synced to cloud in real-time",
                        style = MaterialTheme.typography.bodyMedium, color = Green)
                }
                SettingsDivider()
                SettingsActionRow(
                    icon  = Icons.Default.GridOn,
                    color = Green,
                    title = "Export Full Backup (.xlsx)",
                    subtitle = "All data in 7 sheets \u2014 opens in Excel/Sheets"
                ) { if (isPro) xlsxLauncher.launch("Fynlo_Backup_${System.currentTimeMillis()}.xlsx") else onNavigateToUpgrade() }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.FileDownload,
                    color = Blue,
                    title = "Export JSON Backup",
                    subtitle = "Full backup for restore"
                ) { if (isPro) jsonLauncher.launch("Fynlo_Backup_${System.currentTimeMillis()}.json") else onNavigateToUpgrade() }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.PictureAsPdf,
                    color = Red,
                    title = "Export PDF Report",
                    subtitle = "Financial summary report"
                ) { if (isPro) pdfLauncher.launch("Fynlo_Report_${System.currentTimeMillis()}.pdf") else onNavigateToUpgrade() }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.FileUpload,
                    color = Amber,
                    title = "Restore from JSON",
                    subtitle = "Import a previously exported backup"
                ) { importLauncher.launch("application/json") }

                SettingsDivider()

                SettingsActionRow(
                    icon     = Icons.Default.Calculate,
                    color    = Amber,
                    title    = "Recalculate Balances",
                    subtitle = "Fixes account balances that got out of sync. Run this if Cash in Hand or any account shows a wrong amount."
                ) {
                    viewModel.recalculateAllBalances()
                    android.widget.Toast.makeText(context, "Balances recalculated from transactions ✓", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // â"€â"€ Notifications â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        SettingsSectionLabel("Notifications")
        SettingsCard {
            // Notification toggle
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, null, tint = Amber, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Loan & Budget Reminders",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("Overdue loans, due payments, budget alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked         = notifsEnabled,
                    onCheckedChange = {
                        scope.launch { UserPreferences.setNotificationsEnabled(context, it) }
                        if (it) app.fynlo.notifications.ReminderScheduler.schedule(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Emerald500,
                        checkedTrackColor = Emerald500.copy(alpha = 0.4f))
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Formatting ──────────────────────────────────────────────────────────
        SettingsSectionLabel("Formatting")
        SettingsCard {
            // Currency
            Text("Default Currency", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val currencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SGD", "AUD", "CAD", "JPY")
            var currencyExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = !currencyExpanded },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = defaultCurrency, onValueChange = {}, readOnly = true,
                    label = { Text("Currency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                    currencies.forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = {
                            scope.launch { UserPreferences.setDefaultCurrency(context, code) }
                            currencyExpanded = false
                        })
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // Date Format
            Text("Date Format", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("dd-MM-yyyy", "MM-dd-yyyy", "yyyy-MM-dd").forEach { fmt ->
                    FilterChip(
                        selected = dateFormat == fmt,
                        onClick  = { scope.launch { UserPreferences.setDateFormat(context, fmt) } },
                        label    = { Text(fmt, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))



        // â"€â"€ App Info â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        // PIN manager retained for the Danger Zone reset gate; the PIN &
        // biometric controls now live in Profile & Security (ProfileScreen).
        val pinManager = remember { app.fynlo.data.PinManager(context) }

        SettingsSectionLabel("App Info")
        SettingsCard {
            SettingsActionRow(
                icon     = Icons.Default.BugReport,
                color    = Red,
                title    = "Report a Bug",
                subtitle = "Send feedback via email"
            ) {
                val deviceInfo = buildString {
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
                    appendLine("App Version: ${app.fynlo.BuildConfig.VERSION_NAME} (${app.fynlo.BuildConfig.VERSION_CODE})")
                    appendLine("\nDescribe your issue:\n[Type here]")
                }
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:fynloapp.support@gmail.com")
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Fynlo Bug Report")
                    putExtra(android.content.Intent.EXTRA_TEXT, deviceInfo)
                })
            }
            SettingsDivider()
            SettingsActionRow(
                icon  = Icons.Default.Star,
                color = Amber,
                title = "Rate on Play Store",
                subtitle = "Enjoying Fynlo? Leave us a review!"
            ) {
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=${context.packageName}")))
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                }
            }
            SettingsDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onNavigateToAbout() }.padding(vertical = 6.dp),
                Arrangement.SpaceBetween, Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(Blue.copy(0.12f)), Alignment.Center) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = Blue)
                    }
                    Column {
                        Text("About & Disclaimer",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Text("Legal info, privacy policy, app details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // â"€â"€ Developer (debug only) â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        if (app.fynlo.BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            SettingsSectionLabel("Developer")
            SettingsCard {
                var showSeedConfirm    by remember { mutableStateOf(false) }
                var showCleanupConfirm by remember { mutableStateOf(false) }
                var showRestoreConfirm by remember { mutableStateOf(false) }
                var showWipeConfirm    by remember { mutableStateOf(false) }

                if (showSeedConfirm) AlertDialog(
                    onDismissRequest = { showSeedConfirm = false },
                    title = { Text("Load Test Data?") },
                    text  = { Text("âš ï¸ This will DELETE all existing data and replace with QA test data.") },
                    confirmButton = { Button(onClick = { viewModel.loadDummyData(); showSeedConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("Load") } },
                    dismissButton = { TextButton(onClick = { showSeedConfirm = false }) { Text("Cancel") } }
                )
                if (showCleanupConfirm) AlertDialog(
                    onDismissRequest = { showCleanupConfirm = false },
                    title = { Text("Cleanup Seeder Data?") },
                    text  = { Text("Removes all QA test data from the app and Firestore.") },
                    confirmButton = { Button(onClick = { viewModel.cleanupSeeederData(); showCleanupConfirm = false }) { Text("Cleanup") } },
                    dismissButton = { TextButton(onClick = { showCleanupConfirm = false }) { Text("Cancel") } }
                )
                if (showRestoreConfirm) AlertDialog(
                    onDismissRequest = { showRestoreConfirm = false },
                    title = { Text("Restore Real Data?") },
                    text  = { Text("Clears all transactions and restores Cash in Hand â‚¹3,962 + HDFC Bank â‚¹1,22,500.") },
                    confirmButton = { Button(onClick = { viewModel.restoreRealData(); showRestoreConfirm = false }) { Text("Restore") } },
                    dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } }
                )
                if (showWipeConfirm) AlertDialog(
                    onDismissRequest = { showWipeConfirm = false },
                    title = { Text("Wipe ALL Data?") },
                    text  = { Text("âš ï¸ PERMANENT DESTRUCTION: This will delete everything from this phone AND Google Cloud. This cannot be undone.") },
                    confirmButton = { Button(onClick = { viewModel.wipeAllData(); showWipeConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("WIPE EVERYTHING") } },
                    dismissButton = { TextButton(onClick = { showWipeConfirm = false }) { Text("Cancel") } }
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsActionRow(Icons.Default.BugReport, Red, "Test Crash (Crashlytics)",
                        "Triggers a fatal crash for Firebase verification") {
                        (context.applicationContext as app.fynlo.FynloApplication).triggerTestCrash()
                    }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.Warning, Amber, "Test Non-Fatal (Crashlytics)",
                        "Records a non-fatal exception") {
                        com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                            .recordException(RuntimeException("Fynlo non-fatal test - verify in Firebase"))
                        android.widget.Toast.makeText(context, "Non-fatal sent to Crashlytics", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.Science, Amber, "Load Test Data (QA)",
                        "Seeds fake data for testing") { showSeedConfirm = true }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.CleaningServices, Red, "Cleanup Seeder Data",
                        "Remove QA data from app + Firestore") { showCleanupConfirm = true }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.Restore, Blue, "Restore Real Data",
                        "Reset to real account balances") { showRestoreConfirm = true }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.DeleteForever, Red, "Wipe ALL Data",
                        "Delete all local and cloud records") { showWipeConfirm = true }
                }
            }
        }

        // â"€â"€ Version â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€
        // ── Danger Zone ──────────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.padding(start = 2.dp, bottom = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(Red))
            Text(
                "Danger Zone",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Red
            )
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Red.copy(alpha = 0.08f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Red.copy(0.12f)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(22.dp), tint = Red)
                }
                Column(Modifier.weight(1f)) {
                    Text("Reset All Data",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("Permanently erase everything and start fresh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = {
                    // Gate behind PIN/biometric (same check as the lock screen)
                    // when a PIN is set, otherwise go straight to the warning.
                    if (pinManager.isPinSet) showResetPinGate = true
                    else showResetWarning = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                border   = androidx.compose.foundation.BorderStroke(1.dp, Red)
            ) {
                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reset All Data", fontWeight = FontWeight.SemiBold)
            }
        }

        // Confirmation warning — shown after the PIN gate (if any).
        if (showResetWarning) {
            AlertDialog(
                onDismissRequest = { showResetWarning = false },
                icon  = { Icon(Icons.Default.Warning, null, tint = Red) },
                title = { Text("Reset All Data?") },
                text  = {
                    Text(
                        "This will permanently delete ALL your data — transactions, " +
                        "loans, debts, investments, accounts, budgets and goals. " +
                        "This cannot be undone."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetWarning = false
                            isResetting = true
                            val authManager =
                                (context.applicationContext as app.fynlo.FynloApplication).authManager
                            viewModel.resetAllData(context, authManager) {
                                app.fynlo.util.AppRestarter.restart(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("Yes, Reset Everything") }
                },
                dismissButton = {
                    TextButton(onClick = { showResetWarning = false }) { Text("Cancel") }
                }
            )
        }

        // Blocking progress while the wipe runs — restart kills the process.
        if (isResetting) {
            AlertDialog(
                onDismissRequest = { },
                confirmButton = { },
                title = { Text("Resetting…") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = Red)
                        Text("Erasing all data and restarting")
                    }
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Version ${app.fynlo.BuildConfig.VERSION_NAME} (${app.fynlo.BuildConfig.VERSION_CODE})",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(100.dp))
    }
    }
}

// â"€â"€ Shared composables â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€â"€

@Composable
private fun SettingsSectionLabel(title: String) {
    Row(
        Modifier.padding(start = 2.dp, bottom = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(4.dp).clip(CircleShape).background(Emerald500))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Emerald500
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        Arrangement.spacedBy(14.dp),
        Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(0.1f)),
            Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

