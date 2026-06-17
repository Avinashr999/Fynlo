package app.fynlo.ui.screens

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
    // C18 — split toggles (audit fix #1). Each defaults to the master in the
    // pref-layer Flow so existing-setup users see consistent state on first open.
    val loanRemindersEnabled by UserPreferences.loanRemindersEnabled(context).collectAsState(initial = true)
    val budgetAlertsEnabled  by UserPreferences.budgetAlertsEnabled(context).collectAsState(initial = true)
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


    // C22 (3.2.66) — backup-encryption UI state. Toggle persists for the
    // session only (not on DataStore — encryption is a per-export choice,
    // not a long-term preference). `pendingExportPassword` survives the
    // dialog→SAF gap; `pendingImportBytes` survives the SAF→dialog gap on
    // the restore side when the picked file is detected as encrypted.
    var encryptOnExport       by remember { mutableStateOf(false) }
    var showExportPwdDialog   by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf("") }
    var pendingImportBytes    by remember { mutableStateOf<ByteArray?>(null) }
    var importErrorMessage    by remember { mutableStateOf<String?>(null) }
    var pendingRestoreJson    by remember { mutableStateOf<String?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<app.fynlo.data.BackupRestorePreview?>(null) }
    var restorePreviewError   by remember { mutableStateOf<String?>(null) }

    // C22 (3.2.67) — CSV import state. `pendingCsvRows` holds the parsed
    // CSV after the SAF picker callback completes; presence triggers the
    // column-mapping dialog. Importer result surfaces as a one-shot
    // snackbar-ish line in the dialog before dismiss.
    var pendingCsvRows by remember { mutableStateOf<List<List<String>>?>(null) }
    var csvImportSummary by remember { mutableStateOf<String?>(null) }
    val allAccountsForImport by viewModel.accounts.collectAsState()
    val ledgerReport by viewModel.ledgerAccountabilityReport.collectAsState()
    var showDataExportDialog by remember { mutableStateOf(false) }
    var selectedDataExportScope by remember { mutableStateOf(DataExportScope.WHOLE) }
    var selectedDataExportFormat by remember { mutableStateOf(DataExportFormat.PDF) }
    var showPersonalization by remember { mutableStateOf(false) }
    var showBackupExport by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showFormatting by remember { mutableStateOf(false) }
    var showAppInfo by remember { mutableStateOf(false) }
    var showDeveloperTools by remember { mutableStateOf(false) }

    // ── Export launchers ────────────────────────────────────────────────────
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { scope.launch {
        val json = viewModel.exportAllData()
        val bytes = if (pendingExportPassword.isNotBlank()) {
            // C22 (3.2.66) — encrypt with the password captured pre-SAF.
            // Wipe the password immediately after use; we don't want it
            // sitting in process memory longer than necessary.
            val pwd = pendingExportPassword
            pendingExportPassword = ""
            app.fynlo.logic.EncryptedBackup.encrypt(json, pwd)
        } else json.toByteArray()
        context.contentResolver.openOutputStream(it)?.use { os -> os.write(bytes) }
    }}}

    // 3.2.70 — sample CSV exporter so users can test Import without having
    // a real bank export. Saves a tiny 5-row file via CreateDocument SAF
    // so the user picks where to drop it (Downloads is the obvious choice).
    val csvSampleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        runCatching {
            val sample = buildString {
                appendLine("Date,Description,Amount,Category")
                appendLine("2026-05-20,Salary credit,50000,Salary")
                appendLine("2026-05-21,Coffee at Blue Tokai,-380,Food")
                appendLine("2026-05-22,Uber to office,-145,Transport")
                appendLine("2026-05-23,Amazon — wireless mouse,-1299,Shopping")
                appendLine("2026-05-25,Refund — wrong item returned,1299,Shopping")
            }
            context.contentResolver.openOutputStream(it)?.use { os ->
                os.write(sample.toByteArray(Charsets.UTF_8))
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    "Sample CSV saved. Now tap Import Bank Statement and pick it.",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }}}

    // C22 (3.2.67 → 3.2.68 smoke fix) — Use `OpenDocument` not `GetContent`.
    // `GetContent("*/*")` opens to the recents-only SAF chooser on many
    // Android versions and surfaces an empty "no files" screen if the
    // user has no recent CSVs — user reported "no options coming to save
    // in files". `OpenDocument` with an explicit MIME array launches the
    // full SAF file browser (Downloads, internal storage, Drive, etc.)
    // and lists files matching any of the listed types.
    val csvImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r ->
                val text = r.readText()
                val rows = app.fynlo.logic.CsvParser.parse(text)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    pendingCsvRows = rows
                    csvImportSummary = null
                }
            }
        }.onFailure {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                csvImportSummary = "Couldn't read the file: ${it.message}"
            }
        }
    }}}

    // 3.2.68 smoke fix — same OpenDocument switch as the CSV launcher.
    // GetContent("*/*") opens to recents on many Android versions; users
    // looking for a backup in Downloads couldn't reach it. OpenDocument
    // launches the full SAF browser.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { scope.launch {
        context.contentResolver.openInputStream(it)?.use { ins ->
            val bytes = ins.readBytes()
            if (app.fynlo.logic.EncryptedBackup.isEncrypted(bytes)) {
                // Defer restore until the user supplies the password.
                pendingImportBytes = bytes
            } else {
                val json = String(bytes, Charsets.UTF_8)
                runCatching { app.fynlo.data.BackupRestorePreviewer.preview(json) }
                    .onSuccess { preview ->
                        pendingRestoreJson = json
                        pendingRestorePreview = preview
                        restorePreviewError = null
                    }
                    .onFailure { restorePreviewError = it.message ?: "Backup could not be read." }
            }
        }
    }}}

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        // exportToPDF is now suspend (C02 — runs recalcCoordinator.runAndStamp()
        // first so the PDF reflects fresh state, not whatever was in memory).
        context.contentResolver.openOutputStream(it)?.use { os ->
            // C11 (3.2.40) — pass user's Date Format preference through.
            viewModel.exportToPDF(os, dateFormat = dateFormat)
        }
    }}}

    val dataExportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        val csv = viewModel.exportDataToCSV(selectedDataExportScope.id)
        context.contentResolver.openOutputStream(it)?.use { os ->
            os.write(csv.toByteArray(Charsets.UTF_8))
        }
    }}}

    val dataExportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { scope.launch(Dispatchers.IO) {
        context.contentResolver.openOutputStream(it)?.use { os ->
            viewModel.exportDataToPDF(
                outputStream = os,
                scope = selectedDataExportScope.id,
                dateFormat = dateFormat,
            )
        }
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
                // C11 (3.2.40) — pass user's Date Format preference through.
                viewModel.exportToXLSX(os, dateFormat = dateFormat)
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

    // C22 (3.2.66) — backup-encryption dialogs.
    if (showExportPwdDialog) {
        BackupPasswordDialog(
            mode = BackupPasswordMode.SET,
            onConfirm = { pwd ->
                pendingExportPassword = pwd
                showExportPwdDialog = false
                jsonLauncher.launch("Fynlo_Backup_${System.currentTimeMillis()}.fynloenc")
            },
            onDismiss = { showExportPwdDialog = false },
        )
    }
    if (showDataExportDialog) {
        DataExportDialog(
            selectedScope = selectedDataExportScope,
            selectedFormat = selectedDataExportFormat,
            onScopeChange = { selectedDataExportScope = it },
            onFormatChange = { selectedDataExportFormat = it },
            onDismiss = { showDataExportDialog = false },
            onExport = {
                showDataExportDialog = false
                val filename = app.fynlo.logic.ExportUtility.filename(
                    "Data_${selectedDataExportScope.fileToken}",
                    "Personal",
                    selectedDataExportFormat.ext,
                )
                when (selectedDataExportFormat) {
                    DataExportFormat.CSV -> dataExportCsvLauncher.launch(filename)
                    DataExportFormat.PDF -> dataExportPdfLauncher.launch(filename)
                }
            },
        )
    }
    pendingCsvRows?.let { rows ->
        val allTransactions by viewModel.transactions.collectAsState()
        CsvImportDialog(
            rows = rows,
            accounts = allAccountsForImport.map { it.name },
            existingTransactions = allTransactions,
            onDismiss = { pendingCsvRows = null; csvImportSummary = null },
            onConfirm = { mapping, targetAccount ->
                scope.launch(Dispatchers.IO) {
                    val results = app.fynlo.logic.BankStatementImport.mapRows(
                        rows = rows,
                        columns = mapping,
                        targetAccount = targetAccount,
                        existingTransactions = allTransactions
                    )
                    val ok = results.filterIsInstance<app.fynlo.logic.BankStatementImport.RowResult.Ok>()
                    val unique = ok.filter { !it.isDuplicate }
                    val dups = ok.size - unique.size
                    val skipped = results.size - ok.size

                    for (r in unique) viewModel.addTransaction(r.transaction)

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        csvImportSummary = "Imported ${unique.size} transactions" +
                                           (if (dups > 0) ", $dups duplicates skipped" else "") +
                                           (if (skipped > 0) ", $skipped errors skipped" else "")
                        pendingCsvRows = null
                    }
                }
            },
            initialSummary = csvImportSummary,
        )
    }

    pendingImportBytes?.let { bytes ->
        BackupPasswordDialog(
            mode = BackupPasswordMode.ENTER,
            errorMessage = importErrorMessage,
            onConfirm = { pwd ->
                try {
                    val plain = app.fynlo.logic.EncryptedBackup.decrypt(bytes, pwd)
                    val preview = app.fynlo.data.BackupRestorePreviewer.preview(plain)
                    pendingRestoreJson = plain
                    pendingRestorePreview = preview
                    pendingImportBytes = null
                    importErrorMessage = null
                } catch (e: Exception) {
                    importErrorMessage = e.message ?: "Wrong password or the backup is corrupted."
                }
            },
            onDismiss = {
                pendingImportBytes = null
                importErrorMessage = null
            },
        )
    }

    restorePreviewError?.let { message ->
        AlertDialog(
            onDismissRequest = { restorePreviewError = null },
            title = { Text("Backup cannot be restored") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { restorePreviewError = null }) { Text("OK") }
            },
        )
    }

    pendingRestorePreview?.let { preview ->
        BackupRestorePreviewDialog(
            preview = preview,
            onDismiss = {
                pendingRestoreJson = null
                pendingRestorePreview = null
            },
            onConfirm = {
                val json = pendingRestoreJson
                if (json != null) {
                    pendingRestoreJson = null
                    pendingRestorePreview = null
                    scope.launch(Dispatchers.IO) {
                        runCatching { viewModel.restoreDataNow(json) }
                            .onSuccess {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Restore complete. Backup data loaded.",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                            .onFailure {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    restorePreviewError = it.message ?: "Restore failed."
                                }
                            }
                    }
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PremiumScreenHeader("Settings", "Your Fynlo control room")
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // ── Upgrade to Pro (hidden until billing is enabled) ──────────────────
        SettingsSummaryPanel(
            currencyCode = defaultCurrency,
            dateFormat = dateFormat,
            notificationsEnabled = loanRemindersEnabled || budgetAlertsEnabled,
            encryptedBackups = encryptOnExport,
        )
        Spacer(Modifier.height(18.dp))

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
        SettingsExpandableCard(
            title = "Personalization",
            subtitle = "Theme and display name",
            icon = Icons.Default.Palette,
            color = Emerald500,
            expanded = showPersonalization,
            onToggle = { showPersonalization = !showPersonalization },
        ) {
            // 3.2.21 — Theme picker redesigned to match the Notifications
            // section's Switch pattern. Was a 3-option `SegmentedButtonRow`
            // (System / Light / Dark) from the 3.2.11 chip-sweep; that was
            // technically correct (3-state mutex) but visually inconsistent
            // with the rest of Settings, which uses single-Switch rows for
            // binary toggles. Native Android display-settings UX maps this
            // to: top-level "Follow system" Switch; when OFF, a sub-row
            // "Dark mode" Switch appears. That's now this pattern.
            //
            // State mapping (preserves `ThemeController.darkModeOverride`):
            //   - `null`  → "Follow system" ON,  "Dark mode" row hidden
            //   - `false` → "Follow system" OFF, "Dark mode" Switch OFF
            //   - `true`  → "Follow system" OFF, "Dark mode" Switch ON
            //
            // When the user toggles "Follow system" OFF, the override is
            // seeded with the CURRENT visual state (via `isSystemInDarkTheme()`)
            // so the screen doesn't visually flip — it stays whatever the
            // user is already seeing, just frozen under their control.
            val isCurrentlyDark = androidx.compose.foundation.isSystemInDarkTheme()
            val themeOverride = ThemeController.darkModeOverride
            val followSystem = themeOverride == null

            // Row 1: Follow system theme
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBubble(Icons.Default.PhoneAndroid, Emerald500)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Follow system theme",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("Match your phone's light / dark setting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = followSystem,
                    onCheckedChange = { useSystem ->
                        // Seed override with current visual state on first opt-out
                        // so the screen doesn't flip; user keeps what they see.
                        ThemeController.darkModeOverride = if (useSystem) null else isCurrentlyDark
                        ThemeController.save(context)
                        viewModel.showFeedback(if (useSystem) "Using system theme" else "Theme preference saved")
                    },
                    // 3.2.22 — added explicit uncheckedColors. M3 defaults
                    // were `outline` thumb on `surfaceContainerHighest` track,
                    // both very light greys that disappeared into the
                    // SettingsCard's `surfaceVariant` background in light
                    // mode (user reported "not clearly visible"). Now uses
                    // `onSurfaceVariant` for the unchecked thumb so the dot
                    // is clearly visible against the track in both themes.
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Emerald500,
                        checkedTrackColor   = Emerald500.copy(alpha = 0.4f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
            // Row 2: Dark mode (only shown when "Follow system" is OFF)
            if (!followSystem) {
                SettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconBubble(Icons.Default.DarkMode, Amber)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dark mode",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                        Text("Use dark theme regardless of system",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = themeOverride == true,
                        onCheckedChange = {
                            ThemeController.darkModeOverride = it
                            ThemeController.save(context)
                            viewModel.showFeedback(if (it) "Dark mode saved" else "Light mode saved")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Emerald500,
                            checkedTrackColor = Emerald500.copy(alpha = 0.4f))
                    )
                }
            }
            SettingsDivider()
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Your Name") },
                placeholder   = { Text("Optional — used for greetings") },
                singleLine    = true,
                trailingIcon  = null,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Emerald500,
                    focusedLabelColor    = Emerald500
                )
            )
            if (displayName.isNotBlank()) {
                Button(
                    onClick = {
                        scope.launch {
                            UserPreferences.setUserDisplayName(context, displayName.trim())
                            viewModel.showFeedback("Name saved")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 10.dp, end = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save name")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Cloud Backup ─────────────────────────────────────────────────────
        // ── Backup & Export ──────────────────────────────────────────────────
        SettingsExpandableCard(
            title = "Backup & Export",
            subtitle = "Backups, reports, imports, and repair tools",
            icon = Icons.Default.Backup,
            color = Blue,
            expanded = showBackupExport,
            onToggle = { showBackupExport = !showBackupExport },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (app.fynlo.BuildConfig.DEBUG) {
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
                }

                // C22 (3.2.66) — encryption toggle. Off by default so the
                // existing Export flow is unchanged for users who don't
                // need encryption; flipping on routes the next export
                // through a password dialog → AES-GCM under PBKDF2.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsIconBubble(Icons.Default.Lock, Blue)
                    Column(Modifier.weight(1f)) {
                        Text("Encrypt backup with password",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            if (encryptOnExport) "Next export will be AES-encrypted. Keep the password safe — there's no recovery."
                            else "Export will be plain JSON (readable by anyone with the file).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 3.2.63 lesson — explicit unchecked colors so the Switch
                    // stays visible against the surface in light mode.
                    Switch(
                        checked = encryptOnExport,
                        onCheckedChange = { encryptOnExport = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor    = Color.White,
                            checkedTrackColor    = Emerald500,
                            uncheckedThumbColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor  = MaterialTheme.colorScheme.surface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    )
                }

                SettingsActionRow(
                    icon  = Icons.Default.FileDownload,
                    color = Blue,
                    title = "Export JSON Backup",
                    subtitle = if (encryptOnExport) "Encrypted with password"
                               else "All projects, accounts, entries, loans, debts, investments, and settings"
                ) {
                    if (!isPro) {
                        onNavigateToUpgrade()
                    } else if (encryptOnExport) {
                        // Password dialog → SAF picker → encrypt + write.
                        showExportPwdDialog = true
                    } else {
                        pendingExportPassword = ""
                        jsonLauncher.launch("Fynlo_Backup_${System.currentTimeMillis()}.json")
                    }
                }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.PictureAsPdf,
                    color = Red,
                    title = "Export PDF Report",
                    subtitle = "Financial summary report"
                ) { if (isPro) pdfLauncher.launch("Fynlo_Report_${System.currentTimeMillis()}.pdf") else onNavigateToUpgrade() }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.Share,
                    color = Green,
                    title = "Export Data",
                    subtitle = "Choose whole data or one section as CSV/PDF"
                ) { if (isPro) showDataExportDialog = true else onNavigateToUpgrade() }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.FileUpload,
                    color = Amber,
                    title = "Restore Backup",
                    subtitle = "Preview counts and integrity before replacing current data"
                ) {
                    // 3.2.69 smoke fix — single `*/*` so Samsung / Xiaomi
                    // pickers don't hide files whose declared MIME isn't in
                    // the array. Our reader auto-detects encrypted vs plain
                    // via magic header, so file type doesn't matter at the
                    // picker level.
                    importLauncher.launch(arrayOf("*/*"))
                }

                SettingsDivider()

                if (app.fynlo.BuildConfig.DEBUG) {
                // C22 (3.2.67) — bank statement CSV import.
                // 3.2.70 — discoverable sample for first-time testing. Saves
                // a 5-row example file to user-chosen location so they can
                // immediately exercise the Import flow without needing a
                // real bank export.
                // NOTE: Icons.AutoMirrored.Filled.NoteAdd doesn't exist; use
                // Icons.Default.NoteAdd (deprecation warning is harmless).
                SettingsActionRow(
                    icon  = Icons.Default.Description,
                    color = Carbon500,
                    title = "Save sample CSV (for testing)",
                    subtitle = "Drop a 5-row example file in Downloads, then run Import",
                ) {
                    csvSampleLauncher.launch("fynlo_sample.csv")
                }

                SettingsDivider()
                }

                SettingsActionRow(
                    icon  = Icons.Default.TableChart,
                    color = Blue,
                    title = "Import Bank Statement (CSV)",
                    subtitle = "Map columns from your bank export and bulk-add transactions"
                ) {
                    if (allAccountsForImport.isEmpty()) {
                        // 3.2.68 smoke fix — was set into csvImportSummary which
                        // only renders inside the mapping dialog (never opens
                        // without rows). A Toast surfaces immediately.
                        android.widget.Toast.makeText(
                            context,
                            "Add an account first — imported transactions need a destination.",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        // 3.2.69 smoke fix — was an array including specific CSV
                        // MIMEs plus `*/*`. User reported "navigated to my files
                        // but cant see file" — Samsung / Xiaomi pickers interpret
                        // the MIME array strictly and HIDE files whose declared
                        // MIME isn't in the list (even when `*/*` is included).
                        // Switching to a single-element `arrayOf("*/*")` shows
                        // every file unconditionally. Our parser is tolerant; the
                        // column-mapping dialog gates bad files (zero rows).
                        csvImportLauncher.launch(arrayOf("*/*"))
                    }
                }

                SettingsDivider()

                // C02 step 4: capture the before/after summary so the result
                // dialog can show actual deltas instead of a fire-and-forget Toast.
                var recalcDelta by remember { mutableStateOf<app.fynlo.RecalcDelta?>(null) }
                var recalcInFlight by remember { mutableStateOf(false) }
                val currentProject by viewModel.currentProject.collectAsState()
                val currencyCode = currentProject?.currency ?: "INR"
                val locale = LocalLocale.current.platformLocale
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
                    subtitle = "Fixes account balances that got out of sync. Run this if Personal Cash or any account shows a wrong amount."
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

                SettingsDivider()

                var showLedgerHealth by remember { mutableStateOf(false) }
                SettingsActionRow(
                    icon = Icons.Default.Verified,
                    color = if (ledgerReport.criticalCount > 0) Red else if (ledgerReport.warningCount > 0) Amber else Green,
                    title = "Ledger health",
                    subtitle = "${ledgerReport.headline} · Score ${ledgerReport.score}/100 · ${ledgerReport.issueCount} checks flagged"
                ) { showLedgerHealth = true }

                if (showLedgerHealth) {
                    LedgerHealthDialog(
                        report = ledgerReport,
                        onDismiss = { showLedgerHealth = false },
                    )
                }

                if (app.fynlo.BuildConfig.DEBUG) {
                SettingsDivider()

                // 3.2.72 — diagnostic: show every balance-mutation site so
                // the user can see which subsystem is moving net worth.
                var showAuditLog by remember { mutableStateOf(false) }
                SettingsActionRow(
                    icon  = Icons.Default.History,
                    color = Carbon500,
                    title = "Balance change log",
                    subtitle = "See every account-balance mutation (recurring, sync, manual) since launch"
                ) { showAuditLog = true }

                if (showAuditLog) {
                    BalanceAuditLogDialog(
                        onDismiss = { showAuditLog = false },
                        onClear = { scope.launch { app.fynlo.logic.BalanceAuditLog.clear(context) } },
                    )
                }

                SettingsDivider()

                // 3.2.74 — wipe Firestore + re-push local. Use when stale
                // cloud data is "restoring" itself into local on every sync.
                var showResetCloudConfirm by remember { mutableStateOf(false) }
                var resetCloudInFlight by remember { mutableStateOf(false) }
                SettingsActionRow(
                    icon  = Icons.Default.CloudOff,
                    color = Amber,
                    title = "Reset cloud sync to match local",
                    subtitle = "Wipes Firestore and re-pushes current local data. Use if mystery values keep restoring on launch (see Balance change log)."
                ) { showResetCloudConfirm = true }

                if (showResetCloudConfirm) {
                    AlertDialog(
                        onDismissRequest = { showResetCloudConfirm = false },
                        title = { Text("Reset cloud sync?") },
                        text = {
                            Text(
                                "This wipes all your Fynlo data on Firestore (cloud) and re-pushes the current local data as the new canonical version.\n\n" +
                                "Local data stays untouched. Other devices signed in to the same account will be re-synced from the new cloud state on their next launch.\n\n" +
                                "Use this only if the Balance change log shows SYNC_PULL_DEBT / _BORROWER entries restoring values you don't recognise."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (resetCloudInFlight) return@TextButton
                                    resetCloudInFlight = true
                                    viewModel.resetCloudSyncToLocal { ok ->
                                        resetCloudInFlight = false
                                        showResetCloudConfirm = false
                                        android.widget.Toast.makeText(
                                            context,
                                            if (ok) "Cloud reset. Force-stop and relaunch to verify no SYNC_PULL entries appear."
                                            else "Reset failed — check your network and try again.",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(if (resetCloudInFlight) "Resetting…" else "Reset cloud") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetCloudConfirm = false }) { Text("Cancel") }
                        }
                    )
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
        // C18 fix #1 (3.2.20) — the single "Loan & Budget Reminders" Switch
        // bundled two semantically different alert types. Split into:
        //   - Loan reminders   (overdue loans, due-date reminders for borrowers)
        //   - Budget alerts    (over-budget category warnings, daily-spend nudges)
        // Each toggles its own pref; setting either ON keeps the master
        // `notifications_enabled` ON so the [ReminderScheduler] keeps running.
        // Setting both OFF disables the master too — scheduler stops.
        // Worker-layer differentiation (which alarm class reads which sub-key
        // before firing) is a follow-up; the UI split lands now so users have
        // granular control even if the underlying dispatch is currently unified.
        SettingsExpandableCard(
            title = "Notifications",
            subtitle = "Loan reminders and budget alerts",
            icon = Icons.Default.Notifications,
            color = Amber,
            expanded = showNotifications,
            onToggle = { showNotifications = !showNotifications },
        ) {
            // Loan reminders sub-toggle
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBubble(Icons.Default.Notifications, Amber)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Loan reminders",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("Overdue loans and due-payment alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked         = loanRemindersEnabled,
                    onCheckedChange = {
                        scope.launch {
                            UserPreferences.setLoanRemindersEnabled(context, it)
                            viewModel.showFeedback(if (it) "Loan reminders on" else "Loan reminders off")
                        }
                        // Re-schedule if either toggle goes ON; ReminderScheduler
                        // is idempotent (uses ExistingPeriodicWorkPolicy.KEEP).
                        if (it) app.fynlo.notifications.ReminderScheduler.schedule(context)
                    },
                    // 3.2.22 — added explicit uncheckedColors. M3 defaults
                    // were `outline` thumb on `surfaceContainerHighest` track,
                    // both very light greys that disappeared into the
                    // SettingsCard's `surfaceVariant` background in light
                    // mode (user reported "not clearly visible"). Now uses
                    // `onSurfaceVariant` for the unchecked thumb so the dot
                    // is clearly visible against the track in both themes.
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Emerald500,
                        checkedTrackColor   = Emerald500.copy(alpha = 0.4f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
            SettingsDivider()
            // Budget alerts sub-toggle
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconBubble(Icons.Default.PieChart, Amber)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Budget alerts",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                    Text("Over-budget category warnings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked         = budgetAlertsEnabled,
                    onCheckedChange = {
                        scope.launch {
                            UserPreferences.setBudgetAlertsEnabled(context, it)
                            viewModel.showFeedback(if (it) "Budget alerts on" else "Budget alerts off")
                        }
                        if (it) app.fynlo.notifications.ReminderScheduler.schedule(context)
                    },
                    // 3.2.22 — added explicit uncheckedColors. M3 defaults
                    // were `outline` thumb on `surfaceContainerHighest` track,
                    // both very light greys that disappeared into the
                    // SettingsCard's `surfaceVariant` background in light
                    // mode (user reported "not clearly visible"). Now uses
                    // `onSurfaceVariant` for the unchecked thumb so the dot
                    // is clearly visible against the track in both themes.
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Emerald500,
                        checkedTrackColor   = Emerald500.copy(alpha = 0.4f),
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Formatting ──────────────────────────────────────────────────────────
        SettingsExpandableCard(
            title = "Formatting",
            subtitle = "Currency and date display",
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            color = Green,
            expanded = showFormatting,
            onToggle = { showFormatting = !showFormatting },
        ) {
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
            // Shared label formatter — used both for the field's selected-row
            // display and the per-row dropdown items so they stay in sync.
            // Audit fix #10: format reads `INR  ₹  Indian Rupee` instead of
            // the bare 3-letter code.
            fun currencyLabel(code: String): String {
                val info = CurrencyUtils.supported.firstOrNull { it.code == code }
                return if (info == null) code
                else "${info.code}   ${info.symbol}   ${info.name}"
            }
            var currencyExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = !currencyExpanded },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = currencyLabel(pickerCurrency.ifBlank { defaultCurrency }),
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
                            DropdownMenuItem(text = { Text(currencyLabel(code)) }, onClick = {
                                pickerCurrency = code
                                scope.launch {
                                    UserPreferences.setDefaultCurrency(context, code)
                                    viewModel.showFeedback("Currency set to $code")
                                }
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
                        DropdownMenuItem(text = { Text(currencyLabel(code)) }, onClick = {
                            pickerCurrency = code
                            scope.launch {
                                UserPreferences.setDefaultCurrency(context, code)
                                viewModel.showFeedback("Currency set to $code")
                            }
                            viewModel.recordCurrency(code)
                            currencyExpanded = false
                        })
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // Date Format
            // C18 (3.2.20) — was a SegmentedButtonRow (3.2.11) showing just the
            // pattern strings (`dd-MM-yyyy` / `MM-dd-yyyy` / `yyyy-MM-dd`).
            // The audit asked for example values alongside the pattern so the
            // user sees what they're actually choosing. Segmented buttons
            // don't have room for both pattern + example; switched to an
            // ExposedDropdownMenuBox (same widget as Currency above and the
            // EMI Calculator Frequency picker) where each menu item shows
            // `dd-MM-yyyy (27-05-2026)`. The currently-selected option in the
            // field shows the example only, keeping the field readable at a
            // glance; the patterns are visible once the user opens the menu.
            Text("Date Format", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            val today = remember { java.time.LocalDate.now() }
            val dateFormatOptions = listOf("dd-MM-yyyy", "MM-dd-yyyy", "yyyy-MM-dd")
            // Resolve the example for the currently-selected pattern; this is
            // what's shown in the dropdown field so the user can see the format
            // at a glance rather than having to mentally parse the pattern.
            val dateFormatExample = remember(dateFormat, today) {
                runCatching { today.format(java.time.format.DateTimeFormatter.ofPattern(dateFormat)) }
                    .getOrDefault(today.toString())
            }
            var dateFormatExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dateFormatExpanded,
                onExpandedChange = { dateFormatExpanded = !dateFormatExpanded },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            ) {
                OutlinedTextField(
                    value = "$dateFormat   →   $dateFormatExample",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Date Format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dateFormatExpanded) },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(expanded = dateFormatExpanded, onDismissRequest = { dateFormatExpanded = false }) {
                    dateFormatOptions.forEach { fmt ->
                        val example = runCatching { today.format(java.time.format.DateTimeFormatter.ofPattern(fmt)) }
                            .getOrDefault(today.toString())
                        DropdownMenuItem(
                            text = { Text("$fmt   ($example)") },
                            onClick = {
                                scope.launch {
                                    UserPreferences.setDateFormat(context, fmt)
                                    viewModel.showFeedback("Date format saved")
                                }
                                dateFormatExpanded = false
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))



        // ── App Info ─────────────────────────────────────────────────────────
        // PIN manager retained for the Danger Zone reset gate; the PIN &
        // biometric controls now live in Profile & Security (ProfileScreen).
        val pinManager = remember { app.fynlo.data.PinManager(context) }

        SettingsExpandableCard(
            title = "App Info",
            subtitle = "Support, rating, and legal details",
            icon = Icons.Default.Info,
            color = Blue,
            expanded = showAppInfo,
            onToggle = { showAppInfo = !showAppInfo },
        ) {
            // C18 #4 (3.2.78) — was an email-shortcut; the audit deferred
            // an in-app form. ReportBugDialog captures the report in a
            // structured Crashlytics non-fatal (so support gets it without
            // the user having to remember to actually send the email), and
            // keeps the email fallback for offline / no-Play-Services cases.
            var showBugDialog by remember { mutableStateOf(false) }
            if (showBugDialog) {
                app.fynlo.ui.components.ReportBugDialog(onDismiss = { showBugDialog = false })
            }
            SettingsActionRow(
                icon     = Icons.Default.BugReport,
                color    = Red,
                title    = "Report a Bug",
                subtitle = "In-app form with device info and reference ID"
            ) { showBugDialog = true }
            // C18 fix #5 (3.2.20) — Rate-on-Play-Store gated by positive
            // engagement. The audit's complaint was the rate-prompt being
            // shown immediately rather than after the user has actually
            // used the app. Threshold: ≥5 transactions logged. Fresh
            // installs / first-day users don't see the row at all, so
            // they can't be nudged toward a premature rating they
            // wouldn't otherwise leave. After 5 transactions the user
            // has demonstrably engaged with the core flow, and the row
            // appears under App Info as a normal entry.
            val transactionCount by viewModel.transactions.collectAsState()
            if (transactionCount.size >= 5) {
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
            }
            SettingsDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onNavigateToAbout() }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(Blue.copy(0.12f)), Alignment.Center) {
                        Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = Blue)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("About & Disclaimer",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("Legal info, privacy policy, app details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Developer (debug only) ───────────────────────────────────────────
        if (app.fynlo.BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            SettingsExpandableCard(
                title = "Developer",
                subtitle = "QA and diagnostic tools",
                icon = Icons.Default.Build,
                color = Amber,
                expanded = showDeveloperTools,
                onToggle = { showDeveloperTools = !showDeveloperTools },
            ) {
                var showSeedConfirm    by remember { mutableStateOf(false) }
                var showCleanupConfirm by remember { mutableStateOf(false) }
                var showRestoreConfirm by remember { mutableStateOf(false) }
                var showWipeConfirm    by remember { mutableStateOf(false) }

                if (showSeedConfirm) AlertDialog(
                    onDismissRequest = { showSeedConfirm = false },
                    title = { Text("Load Test Data?") },
                    text  = { Text("Warning: this will delete all existing data and replace it with QA test data.") },
                    confirmButton = { Button(onClick = { viewModel.loadDummyData(); showSeedConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("Load") } },
                    dismissButton = { TextButton(onClick = { showSeedConfirm = false }) { Text("Cancel") } }
                )
                if (showCleanupConfirm) AlertDialog(
                    onDismissRequest = { showCleanupConfirm = false },
                    title = { Text("Cleanup Seeder Data?") },
                    text  = { Text("Removes all QA test data from the app and Firestore.") },
                    // C18 (3.2.20) — destructive action confirm button gets the
                    // Red treatment for parity with Load Test Data / Wipe ALL.
                    // Pre-3.2.20 this was a default-coloured Button, which the
                    // audit flagged as inconsistent destructive-dialog colour.
                    confirmButton = { Button(onClick = { viewModel.cleanupSeeederData(); showCleanupConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Red)
                    ) { Text("Cleanup") } },
                    dismissButton = { TextButton(onClick = { showCleanupConfirm = false }) { Text("Cancel") } }
                )
                if (showRestoreConfirm) AlertDialog(
                    onDismissRequest = { showRestoreConfirm = false },
                    title = { Text("Restore Real Data?") },
                    text  = { Text("Clears all transactions and restores Personal Cash ₹3,962 + HDFC Bank ₹1,22,500.") },
                    confirmButton = { Button(onClick = { viewModel.restoreRealData(); showRestoreConfirm = false }) { Text("Restore") } },
                    dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } }
                )
                if (showWipeConfirm) AlertDialog(
                    onDismissRequest = { showWipeConfirm = false },
                    title = { Text("Wipe ALL Data?") },
                    text  = { Text("Permanent destruction: this will delete everything from this phone and Google Cloud. This cannot be undone.") },
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
        Spacer(Modifier.height(48.dp))
    }
    }
}

// ── Shared composables ──────────────────────────────────────────────────────

@Composable
private fun SettingsSectionLabel(title: String) {
    // C18 (3.2.20) — removed the emerald `•` bullet + emerald-coloured text.
    // The audit called this inconsistent with the rest of the app, where
    // section headers are plain bold on default surface colour. The Danger
    // Zone header (further below) keeps its red bullet because it serves
    // a distinct attention-grabbing role; ordinary "Personalization",
    // "Notifications", "Formatting" etc. don't need it.
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.8.dp, TemplateBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsExpandableCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(0.8.dp, TemplateBorder),
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle()
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsIconBubble(icon, color)
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = TemplateBorder,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(start = 54.dp, top = 6.dp, bottom = 6.dp),
        thickness = 0.5.dp,
        color     = TemplateBorder
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
@Composable
private fun SettingsSummaryPanel(
    currencyCode: String,
    dateFormat: String,
    notificationsEnabled: Boolean,
    encryptedBackups: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplatePanelRadius),
        color = Emerald700,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.12f)),
                    Alignment.Center,
                ) {
                    Icon(Icons.Default.Tune, null, Modifier.size(22.dp), tint = Emerald100)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Preferences ready",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        "Currency, backups, alerts, and exports in one place",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatusPill(
                    icon = Icons.Default.Payments,
                    label = currencyCode,
                    color = Emerald500,
                    modifier = Modifier.weight(1f),
                )
                SettingsStatusPill(
                    icon = Icons.Default.Event,
                    label = dateFormat,
                    color = SemanticBlue,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsStatusPill(
                    icon = Icons.Default.Notifications,
                    label = if (notificationsEnabled) "Alerts on" else "Alerts off",
                    color = SemanticAmber,
                    modifier = Modifier.weight(1f),
                )
                SettingsStatusPill(
                    icon = Icons.Default.Lock,
                    label = if (encryptedBackups) "Encrypted" else "Plain backup",
                    color = SemanticBlue,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingsStatusPill(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 38.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.18f)),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = color)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsIconBubble(icon: ImageVector, color: Color) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
        Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = color)
    }
}

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

private enum class DataExportFormat(val label: String, val ext: String) {
    PDF("PDF", "pdf"),
    CSV("CSV", "csv"),
}

private enum class DataExportScope(
    val id: String,
    val label: String,
    val fileToken: String,
    val subtitle: String,
) {
    WHOLE("whole", "Whole Data", "Whole_Data", "All readable sections"),
    TRANSACTIONS("transactions", "Transactions", "Transactions", "History rows only"),
    ACCOUNTS("accounts", "Accounts", "Accounts", "Account balances"),
    LENDING("lending", "Lending", "Lending", "Loans you gave"),
    DEBTS("debts", "Debts", "Debts", "Money you owe"),
    INVESTMENTS("investments", "Investments", "Investments", "Investment records"),
    PEOPLE("people", "People", "People", "Contact book"),
    BUDGETS("budgets", "Budgets", "Budgets", "Budget limits"),
    GOALS("goals", "Goals", "Goals", "Savings goals"),
}

@Composable
private fun DataExportDialog(
    selectedScope: DataExportScope,
    selectedFormat: DataExportFormat,
    onScopeChange: (DataExportScope) -> Unit,
    onFormatChange: (DataExportFormat) -> Unit,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Data") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DataExportFormat.entries.forEach { format ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onFormatChange(format) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = { onFormatChange(format) },
                            )
                            Text(format.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Text(
                    "Choose what to export",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DataExportScope.entries.forEach { scope ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onScopeChange(scope) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedScope == scope,
                                onClick = { onScopeChange(scope) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    scope.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    scope.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

            }
        },
        confirmButton = {
            Button(onClick = onExport) {
                Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
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
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        Arrangement.spacedBy(12.dp),
        Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(0.12f)),
            Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(21.dp), tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text(title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
    }
}

@Composable
private fun LedgerHealthDialog(
    report: app.fynlo.logic.LedgerAccountabilityReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Ledger health")
                Text(
                    "${report.headline} · Score ${report.score}/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { LedgerHealthSummary(report) }
                if (report.issues.isNotEmpty()) {
                    item { LedgerDialogSectionTitle("Checks to review") }
                    items(report.issues.take(12)) { issue -> LedgerIssueRow(issue) }
                    if (report.issues.size > 12) {
                        item {
                            Text(
                                "+${report.issues.size - 12} more checks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            "No ledger integrity problems found in the current project.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (report.duplicates.isNotEmpty()) {
                    item { LedgerDialogSectionTitle("Possible duplicates") }
                    items(report.duplicates.take(5)) { duplicate ->
                        LedgerInfoCard(
                            title = duplicate.title,
                            detail = duplicate.detail,
                            accent = Amber,
                        )
                    }
                }
                if (report.trails.isNotEmpty()) {
                    item { LedgerDialogSectionTitle("Money trails") }
                    items(report.trails.take(8)) { trail ->
                        LedgerInfoCard(
                            title = "${trail.referenceId} · ${trail.title}",
                            detail = trail.route,
                            accent = Green,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun LedgerHealthSummary(report: app.fynlo.logic.LedgerAccountabilityReport) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LedgerSummaryMetric("Critical", report.criticalCount.toString(), Red)
                LedgerSummaryMetric("Warnings", report.warningCount.toString(), Amber)
                LedgerSummaryMetric("Traces", report.linkedRecords.toString(), Green)
            }
            Text(
                "Sync: ${report.syncSummary}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Duplicates: ${report.duplicateCount} · Missing trace: ${report.missingTraceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LedgerSummaryMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LedgerDialogSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun LedgerIssueRow(issue: app.fynlo.logic.LedgerIssue) {
    val color = when (issue.severity) {
        app.fynlo.logic.LedgerIssueSeverity.CRITICAL -> Red
        app.fynlo.logic.LedgerIssueSeverity.WARNING -> Amber
        app.fynlo.logic.LedgerIssueSeverity.INFO -> Blue
    }
    LedgerInfoCard(
        title = issue.title,
        detail = issue.detail,
        accent = color,
    )
}

@Composable
private fun LedgerInfoCard(
    title: String,
    detail: String,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(9.dp).padding(top = 6.dp).background(accent, CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
}

/**
 * C22 (3.2.66) — password dialog for encrypted-backup export/import.
 *
 * - [BackupPasswordMode.SET]: export flow. Requires a second "confirm
 *   password" field to defend against typos (no recovery if the user
 *   exports with a misremembered password). Minimum length 8.
 * - [BackupPasswordMode.ENTER]: restore flow. Single field; the magic
 *   header is what told us the file is encrypted, so we just need the
 *   password to decrypt. [errorMessage] surfaces "wrong password" from
 *   a previous failed attempt without dismissing the dialog.
 */
/**
 * C22 (3.2.67) — column-mapping dialog for CSV import.
 *
 * Surfaces the parsed CSV headers (first row) as the option set for four
 * column dropdowns: Date, Description, Amount, Category (optional). The
 * user also picks a target Account; all mapped rows land there so the
 * orphan-account regression from 3.2.59 can't recur.
 *
 * Live preview of the first 3 mapped rows updates as the user changes
 * mappings — gives them confidence the columns are right before they
 * commit. Skipped-row count surfaces in the button label.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CsvImportDialog(
    rows: List<List<String>>,
    accounts: List<String>,
    existingTransactions: List<app.fynlo.data.model.Transaction> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (app.fynlo.logic.BankStatementImport.ColumnMap, String) -> Unit,
    initialSummary: String?,
) {
    val header = rows.firstOrNull().orEmpty()
    val dataRowCount = (rows.size - 1).coerceAtLeast(0)

    // Default heuristic picks: scan headers for the obvious names; fall
    // back to index 0/1/2 if no match (works for unlabelled exports).
    fun findCol(vararg keywords: String): Int {
        val i = header.indexOfFirst { h -> keywords.any { k -> h.contains(k, ignoreCase = true) } }
        return if (i >= 0) i else -1
    }
    var dateCol     by remember { mutableStateOf(findCol("date", "txn date", "value date").let { if (it < 0) 0 else it }) }
    var descCol     by remember { mutableStateOf(findCol("desc", "narration", "particulars", "memo").let { if (it < 0) 1.coerceAtMost(header.lastIndex.coerceAtLeast(0)) else it }) }
    var amountCol   by remember { mutableStateOf(findCol("amount", "amt", "debit", "credit").let { if (it < 0) 2.coerceAtMost(header.lastIndex.coerceAtLeast(0)) else it }) }
    // -1 sentinel = "None" (no category column).
    var categoryCol by remember { mutableStateOf(findCol("category", "cat")) }
    var targetAccount by remember { mutableStateOf(accounts.firstOrNull().orEmpty()) }

    val mapping = app.fynlo.logic.BankStatementImport.ColumnMap(
        dateCol = dateCol,
        descriptionCol = descCol,
        amountCol = amountCol,
        categoryCol = if (categoryCol < 0) null else categoryCol,
    )
    val preview = remember(rows, mapping, targetAccount, existingTransactions) {
        if (rows.size > 1 && targetAccount.isNotBlank()) {
            app.fynlo.logic.BankStatementImport.mapRows(rows.take(4), mapping, targetAccount, existingTransactions)
        } else emptyList()
    }
    val previewOk = preview.filterIsInstance<app.fynlo.logic.BankStatementImport.RowResult.Ok>()
    val previewSkipped = preview.size - previewOk.size

    app.fynlo.ui.components.FormDialog(
        title = "Map CSV columns",
        onDismiss = onDismiss,
    ) {
        if (initialSummary != null) {
            Text(initialSummary, style = MaterialTheme.typography.bodySmall,
                color = if (initialSummary.startsWith("Imported")) Emerald500
                        else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "$dataRowCount data rows detected. Pick which column holds each piece of information.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        // Header preview chips so the user sees their actual column names.
        if (header.isNotEmpty()) {
            app.fynlo.ui.components.FormSectionLabel("Detected headers")
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                header.forEachIndexed { i, h ->
                    AssistChip(
                        onClick = {},
                        label = { Text("${i + 1}. ${h.take(18)}", style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        CsvColumnPicker("Date column",        header, dateCol,     allowNone = false) { dateCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Description column", header, descCol,     allowNone = false) { descCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Amount column",      header, amountCol,   allowNone = false) { amountCol = it }
        Spacer(Modifier.height(10.dp))
        CsvColumnPicker("Category column (optional)", header, categoryCol, allowNone = true) { categoryCol = it }

        Spacer(Modifier.height(14.dp))
        app.fynlo.ui.components.FormSectionLabel("Import into account")
        Spacer(Modifier.height(6.dp))
        var acctExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = acctExpanded, onExpandedChange = { acctExpanded = !acctExpanded }) {
            OutlinedTextField(
                value = targetAccount,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = acctExpanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = acctExpanded, onDismissRequest = { acctExpanded = false }) {
                accounts.forEach { acct ->
                    DropdownMenuItem(text = { Text(acct) },
                        onClick = { targetAccount = acct; acctExpanded = false })
                }
            }
        }

        if (preview.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            app.fynlo.ui.components.FormSectionLabel("Preview (first ${preview.size} rows)")
            Spacer(Modifier.height(6.dp))
            preview.forEach { r ->
                when (r) {
                    is app.fynlo.logic.BankStatementImport.RowResult.Ok -> {
                        val t = r.transaction
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${if (r.isDuplicate) "Duplicate" else "Ready"} · ${t.date} · ${t.category} · ${"%.2f".format(t.amount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (r.isDuplicate) SemanticAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is app.fynlo.logic.BankStatementImport.RowResult.Skip -> {
                        Text(
                            "Skipped row ${r.rowIndex}: ${r.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (previewSkipped > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fix the date/amount column picks if too many rows skip.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        val canConfirm = dataRowCount > 0 && targetAccount.isNotBlank() && previewOk.isNotEmpty()
        Button(
            onClick = { onConfirm(mapping, targetAccount) },
            enabled = canConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text(
                "Import $dataRowCount rows into $targetAccount",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
        if (!canConfirm) {
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    targetAccount.isBlank() -> "Pick a target account."
                    previewOk.isEmpty()     -> "Preview rows all skip — check your column picks."
                    else                    -> "No data rows to import."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CsvColumnPicker(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    allowNone: Boolean,
    onPick: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selectedIndex < 0          -> "None"
        selectedIndex < headers.size -> "${selectedIndex + 1}. ${headers[selectedIndex]}"
        else                       -> "Column ${selectedIndex + 1}"
    }
    Column {
        app.fynlo.ui.components.FormSectionLabel(label)
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = display,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowNone) {
                    DropdownMenuItem(text = { Text("None") },
                        onClick = { onPick(-1); expanded = false })
                }
                headers.forEachIndexed { i, h ->
                    DropdownMenuItem(
                        text = { Text("${i + 1}. $h") },
                        onClick = { onPick(i); expanded = false }
                    )
                }
            }
        }
    }
}

/**
 * 3.2.72 — diagnostic dialog showing every account-balance mutation.
 *
 * Entries are reactive (StateFlow from BalanceAuditLog.observe) so the
 * list updates without re-opening the dialog if a sync fires while it's
 * visible. Most useful as: open the dialog, force-stop the app, relaunch,
 * watch what SYNC_PULL entries appear at the top — that's the "what
 * mutated on launch" trace.
 */
@Composable
private fun BalanceAuditLogDialog(
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entries by app.fynlo.logic.BalanceAuditLog.observe(context).collectAsState(initial = emptyList())
    val dateFmt = remember {
        java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
            .withZone(java.time.ZoneId.systemDefault())
    }

    app.fynlo.ui.components.FormDialog(
        title = "Balance change log",
        onDismiss = onDismiss,
    ) {
        Text(
            if (entries.isEmpty()) "No balance changes recorded yet. Once you add, edit, sync, or recurring transactions fire, they'll show up here newest-first."
            else "${entries.size} balance mutation${if (entries.size == 1) "" else "s"} recorded (newest first, capped at 200). " +
                 "Look for SYNC_PULL entries on relaunch — those are Firestore overwriting your local balance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(entries, key = { it.timestamp.toString() + it.account + it.source }) { entry ->
                    AuditEntryRow(entry, dateFmt)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Clear log") }
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
            ) { Text("Close") }
        }
    }
}

@Composable
private fun AuditEntryRow(
    entry: app.fynlo.logic.BalanceAuditLog.Entry,
    dateFmt: java.time.format.DateTimeFormatter,
) {
    // Tint the row by source so SYNC_PULL stands out — that's the one
    // the diagnostic exists to flag.
    val tintColor = when (entry.source) {
        "SYNC_PULL"          -> SemanticAmber
        "RECURRING_WORKER"   -> Blue
        "MANUAL_TXN"         -> Emerald500
        "DELETE_TXN"         -> MaterialTheme.colorScheme.error
        "QUICK_EDIT_BALANCE" -> SemanticAmber
        else                 -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val sign = when {
        entry.delta > 0  -> "+"
        entry.delta < 0  -> "−"
        else             -> ""
    }
    val deltaText = "$sign${"%.2f".format(kotlin.math.abs(entry.delta))}"
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tintColor.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    "${entry.source} · ${entry.account}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = tintColor,
                )
                Text(
                    deltaText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (entry.delta < 0) MaterialTheme.colorScheme.error else Emerald500,
                )
            }
            Text(
                "${dateFmt.format(java.time.Instant.ofEpochMilli(entry.timestamp))} — ${entry.note}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class BackupPasswordMode { SET, ENTER }

@Composable
private fun BackupRestorePreviewDialog(
    preview: app.fynlo.data.BackupRestorePreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This will replace the data currently on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    preview.counts.filter { it.second > 0 }.forEach { (label, count) ->
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodySmall)
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
                if (preview.warnings.isEmpty()) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Integrity check passed", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Verified, null, Modifier.size(16.dp), tint = Emerald500) },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Review warnings",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = SemanticAmber,
                        )
                        preview.warnings.take(4).forEach { warning ->
                            Text(
                                warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SemanticRed),
            ) { Text("Replace data") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BackupPasswordDialog(
    mode: BackupPasswordMode,
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm  by remember { mutableStateOf("") }
    var showText by remember { mutableStateOf(false) }

    val title = when (mode) {
        BackupPasswordMode.SET   -> "Set backup password"
        BackupPasswordMode.ENTER -> "Enter backup password"
    }

    val tooShort   = mode == BackupPasswordMode.SET && password.isNotEmpty() && password.length < 8
    val mismatch   = mode == BackupPasswordMode.SET && confirm.isNotEmpty() && confirm != password
    val canConfirm = when (mode) {
        BackupPasswordMode.SET   -> password.length >= 8 && confirm == password
        BackupPasswordMode.ENTER -> password.isNotEmpty()
    }

    val visualTransformation =
        if (showText) androidx.compose.ui.text.input.VisualTransformation.None
        else          androidx.compose.ui.text.input.PasswordVisualTransformation()

    app.fynlo.ui.components.FormDialog(title = title, onDismiss = onDismiss) {
        if (mode == BackupPasswordMode.SET) {
            Text(
                "There's no recovery if you lose this password. " +
                "Write it down somewhere safe.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }

        app.fynlo.ui.components.FormSectionLabel("Password")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("At least 8 characters") },
            singleLine = true,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            isError = tooShort,
            supportingText = if (tooShort) {{ Text("Use at least 8 characters.") }} else null,
        )

        if (mode == BackupPasswordMode.SET) {
            Spacer(Modifier.height(12.dp))
            app.fynlo.ui.components.FormSectionLabel("Confirm password")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                placeholder = { Text("Re-type the same password") },
                singleLine = true,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                isError = mismatch,
                supportingText = if (mismatch) {{ Text("Passwords don't match.") }} else null,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = showText,
                onCheckedChange = { showText = it },
            )
            Text("Show password", style = MaterialTheme.typography.labelMedium)
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = { onConfirm(password) },
            enabled  = canConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text(when (mode) {
                BackupPasswordMode.SET   -> "Encrypt & save"
                BackupPasswordMode.ENTER -> "Decrypt & restore"
            }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}
