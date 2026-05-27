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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.FinanceViewModel
import app.fynlo.data.UserPreferences
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.CurrencyUtils
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

    // ── C04 Stage 3: currency picker — recency-then-locale prefill ────────
    // Use `LocalConfiguration.current.locales[0]` so in-app language overrides
    // (system per-app locale) are respected. The picker's selected row comes
    // from `viewModel.rememberLastCurrencyOrLocale(...)` — the most-recent
    // pick if any, otherwise the device locale's currency code with an "INR"
    // fallback for locales without a country. The reactive top-N list drives
    // the "Recently used" group at the top of the dropdown.
    val pickerLocale = LocalConfiguration.current.locales[0]
    var pickerCurrency by remember { mutableStateOf("") }
    LaunchedEffect(pickerLocale, defaultCurrency) {
        // Re-resolve whenever locale changes or the persisted default updates
        // (e.g., from another surface). `defaultCurrency` is the canonical
        // persisted value; the recency layer wins when present so subsequent
        // picks survive re-open. Falls through to locale → "INR" on a fresh
        // install where no pick has been recorded yet.
        pickerCurrency = viewModel.rememberLastCurrencyOrLocale(pickerLocale)
    }
    val recentCurrencies by viewModel.observeRecentCurrencies()
        .collectAsState(initial = emptyList())

    // Sync display name from flow on first load
    LaunchedEffect(displayNameFlow) { if (displayName.isEmpty()) displayName = displayNameFlow }


    // ── Export launchers ────────────────────────────────────────────────────
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
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        // exportToPDF is now suspend (C02 — runs recalcCoordinator.runAndStamp()
        // first so the PDF reflects fresh state, not whatever was in memory).
        context.contentResolver.openOutputStream(it)?.use { os -> viewModel.exportToPDF(os) }
    }}}

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        // Route XLSX through viewModel.exportToXLSX so it gets the same
        // recalc-then-export contract as PDF / CSV / JSON. Previously this
        // launcher called ExcelExportUtility.generateFullBackup directly and
        // bypassed any pre-step — that's the C02 gap closed.
        runCatching {
            context.contentResolver.openOutputStream(it)?.use { os ->
                viewModel.exportToXLSX(os)
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
            // 3.2.11 chip-sweep: 3-option mutually-exclusive theme toggle → SegmentedButtonRow.
            // `icon = {}` per the 3.2.8 lesson.
            val themeOptions = listOf(
                null  to "System",
                false to "Light",
                true  to "Dark",
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            ) {
                themeOptions.forEachIndexed { idx, (value, label) ->
                    SegmentedButton(
                        selected = ThemeController.darkModeOverride == value,
                        onClick = { ThemeController.darkModeOverride = value; ThemeController.save(context) },
                        shape = SegmentedButtonDefaults.itemShape(idx, themeOptions.size),
                        icon = {},
                        label = { Text(label) },
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

        // ── Cloud Backup ─────────────────────────────────────────────────────
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

                // C02 step 4: capture the before/after summary so the result
                // dialog can show actual deltas instead of a fire-and-forget Toast.
                var recalcDelta by remember { mutableStateOf<app.fynlo.RecalcDelta?>(null) }
                var recalcInFlight by remember { mutableStateOf(false) }
                val currentProject by viewModel.currentProject.collectAsState()
                val currencyCode = currentProject?.currency ?: "INR"
                val locale = java.util.Locale.getDefault()
                fun fmtMoney(v: Double): String = CurrencyFormatter.detail(v, currencyCode, locale)
                fun fmtDelta(v: Double): String = when {
                    kotlin.math.abs(v) < 0.5 -> "no change"
                    v > 0 -> "+${fmtMoney(v)}"
                    else  -> "−${fmtMoney(-v)}"
                }

                SettingsActionRow(
                    icon     = Icons.Default.Calculate,
                    color    = Amber,
                    title    = "Recalculate Balances",
                    subtitle = "Fixes account balances that got out of sync. Run this if Cash in Hand or any account shows a wrong amount."
                ) {
                    if (recalcInFlight) return@SettingsActionRow
                    recalcInFlight = true
                    scope.launch(Dispatchers.IO) {
                        val delta = viewModel.recalculateAllBalancesCapturingDelta()
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            recalcDelta = delta
                            recalcInFlight = false
                        }
                    }
                }

                recalcDelta?.let { delta ->
                    AlertDialog(
                        onDismissRequest = { recalcDelta = null },
                        title = { Text("Balances recalculated") },
                        text = {
                            if (delta.isNoOp) {
                                Text(
                                    "Your data was already up to date — every total stayed the same. " +
                                    "This is the normal outcome after the C01 fix landed; " +
                                    "the recalc is now safe to run any time."
                                )
                            } else {
                                Column {
                                    Text(
                                        "Net worth: ${fmtMoney(delta.before.netWorth)} → " +
                                        "${fmtMoney(delta.after.netWorth)} (${fmtDelta(delta.netWorthChange)})"
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Receivables: ${fmtDelta(delta.receivablesChange)}")
                                    Text("Cash: ${fmtDelta(delta.cashChange)}")
                                    Text("Investments (current value): ${fmtDelta(delta.investmentsChange)}")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { recalcDelta = null }) { Text("OK") }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Notifications ────────────────────────────────────────────────────
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
            // Currency — C04 Stage 3 grouped picker (recently used at top,
            // then full alphabetical list). The displayed/selected row comes
            // from `pickerCurrency` (recency-then-locale resolver); selecting
            // a row records into both the canonical pref and the recency
            // tracker so the next open prefills with that pick.
            Text("Default Currency", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val fullCurrencies = remember {
                CurrencyUtils.supported.map { it.code }.sorted()
            }
            val groupedOrder = remember(recentCurrencies, fullCurrencies) {
                buildCurrencyPickerOrder(recentCurrencies, fullCurrencies)
            }
            var currencyExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = !currencyExpanded },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = pickerCurrency.ifBlank { defaultCurrency },
                    onValueChange = {}, readOnly = true,
                    label = { Text("Currency") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                    // "Recently used" group — hidden entirely on fresh install
                    // (when `observeRecentCurrencies` emits an empty list).
                    if (recentCurrencies.isNotEmpty()) {
                        Text(
                            "Recently used",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        recentCurrencies.forEach { code ->
                            DropdownMenuItem(text = { Text(code) }, onClick = {
                                pickerCurrency = code
                                scope.launch { UserPreferences.setDefaultCurrency(context, code) }
                                viewModel.recordCurrency(code)
                                currencyExpanded = false
                            })
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    // Full alphabetical list (always shown). Recency-driven
                    // entries appear in both groups — the audit's reference
                    // UX explicitly keeps the canonical entry visible in the
                    // main list so users don't have to scroll past their
                    // recents to re-find a familiar code.
                    fullCurrencies.forEach { code ->
                        DropdownMenuItem(text = { Text(code) }, onClick = {
                            pickerCurrency = code
                            scope.launch { UserPreferences.setDefaultCurrency(context, code) }
                            viewModel.recordCurrency(code)
                            currencyExpanded = false
                        })
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // Date Format
            Text("Date Format", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            // 3.2.11 chip-sweep: 3-option mutually-exclusive date format → SegmentedButtonRow.
            // `icon = {}` per the 3.2.8 lesson.
            val dateFormatOptions = listOf("dd-MM-yyyy", "MM-dd-yyyy", "yyyy-MM-dd")
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
            ) {
                dateFormatOptions.forEachIndexed { idx, fmt ->
                    SegmentedButton(
                        selected = dateFormat == fmt,
                        onClick = { scope.launch { UserPreferences.setDateFormat(context, fmt) } },
                        shape = SegmentedButtonDefaults.itemShape(idx, dateFormatOptions.size),
                        icon = {},
                        label = { Text(fmt, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))



        // ── App Info ─────────────────────────────────────────────────────────
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

        // ── Developer (debug only) ───────────────────────────────────────────
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
                    text  = { Text("⚠️ This will DELETE all existing data and replace with QA test data.") },
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
                    text  = { Text("Clears all transactions and restores Cash in Hand ₹3,962 + HDFC Bank ₹1,22,500.") },
                    confirmButton = { Button(onClick = { viewModel.restoreRealData(); showRestoreConfirm = false }) { Text("Restore") } },
                    dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } }
                )
                if (showWipeConfirm) AlertDialog(
                    onDismissRequest = { showWipeConfirm = false },
                    title = { Text("Wipe ALL Data?") },
                    text  = { Text("⚠️ PERMANENT DESTRUCTION: This will delete everything from this phone AND Google Cloud. This cannot be undone.") },
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

        // ── Version ──────────────────────────────────────────────────────────
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

// ── Shared composables ──────────────────────────────────────────────────────

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

/**
 * C04 Stage 3 — pure helper for grouped currency-picker display order.
 *
 * Merges the user's recently-used currency codes (most-recent first) with the
 * full curated list, producing a single flat sequence where:
 *   1. Recent entries come first (preserving their order).
 *   2. Then every entry from [full] that isn't already in [recent], in the
 *      order [full] supplies (callers pass it alphabetically sorted).
 *   3. No duplicates — a code in both [recent] and [full] appears exactly
 *      once, in its recent-group position.
 *
 * Exposed for unit testing — see `CurrencyPickerOrderDataIntegrityTest`.
 * The composable above keeps the two groups visually separated (with a
 * divider) rather than using this flat form, but the dedupe/order contract
 * is the load-bearing piece, hence the test coverage.
 */
fun buildCurrencyPickerOrder(recent: List<String>, full: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    val out  = mutableListOf<String>()
    for (code in recent) {
        if (code.isNotBlank() && seen.add(code)) out += code
    }
    for (code in full) {
        if (code.isNotBlank() && seen.add(code)) out += code
    }
    return out
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

