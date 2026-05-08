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
import app.fynlo.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Green = Color(0xFF059669)
private val Blue  = Color(0xFF3B82F6)
private val Red   = Color(0xFFEF4444)
private val Amber = Color(0xFFF59E0B)

@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    onNavigateToAbout: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // â”€â”€ Export launchers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let {
        context.contentResolver.openOutputStream(it)?.use { os ->
            os.write(viewModel.exportToCSV().toByteArray())
        }
    }}

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
            }
        }
    }}}

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text(
            "Settings",
            style    = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )

        // â”€â”€ Appearance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("Appearance")
        SettingsCard {
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
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ Cloud Backup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("Cloud Backup")
        Card(
            Modifier.fillMaxWidth(),
            RoundedCornerShape(16.dp),
            CardDefaults.cardColors(containerColor = Green.copy(alpha = 0.08f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Green.copy(0.15f)),
                    Alignment.Center) {
                    Icon(Icons.Default.CloudDone, null, Modifier.size(20.dp), tint = Green)
                }
                Column {
                    Text("Auto-Backup Active",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = Green)
                    Text("Data synced to Google Firestore in real-time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ Export & Backup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("Export & Backup")
        SettingsCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsActionRow(
                    icon  = Icons.Default.GridOn,
                    color = Green,
                    title = "Export Full Backup (.xlsx)",
                    subtitle = "All data in 7 sheets â€” opens in Excel/Sheets"
                ) { xlsxLauncher.launch("CashMemo_Backup_${System.currentTimeMillis()}.xlsx") }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.FileDownload,
                    color = Blue,
                    title = "Export JSON Backup",
                    subtitle = "Full backup for restore"
                ) { jsonLauncher.launch("CashMemo_Backup_${System.currentTimeMillis()}.json") }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.TableChart,
                    color = Blue,
                    title = "Export Expenses (.csv)",
                    subtitle = "Transactions in spreadsheet format"
                ) { csvLauncher.launch("CashMemo_Expenses_${System.currentTimeMillis()}.csv") }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.PictureAsPdf,
                    color = Red,
                    title = "Export PDF Report",
                    subtitle = "Financial summary report"
                ) { pdfLauncher.launch("CashMemo_Report_${System.currentTimeMillis()}.pdf") }

                SettingsDivider()

                SettingsActionRow(
                    icon  = Icons.Default.FileUpload,
                    color = Amber,
                    title = "Restore from JSON",
                    subtitle = "Import a previously exported backup"
                ) { importLauncher.launch("application/json") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ Notifications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("Notifications")
        SettingsCard {
            SettingsActionRow(
                icon     = Icons.Default.Notifications,
                color    = Amber,
                title    = "Test Notifications",
                subtitle = "Schedule daily loan due date & budget alerts"
            ) {
                app.fynlo.notifications.ReminderScheduler.schedule(context)
                android.widget.Toast.makeText(
                    context, "Daily reminders scheduled!", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ Security â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("Security")
        val pinManager = remember { app.fynlo.data.PinManager(context) }
        var pinSet      by remember { mutableStateOf(pinManager.isPinSet) }
        var showPinSetup by remember { mutableStateOf(false) }
        if (showPinSetup) {
            PinScreen(
                mode      = PinMode.SET,
                onSuccess = { pinSet = pinManager.isPinSet; showPinSetup = false },
                onSkip    = { showPinSetup = false }
            )
        }
        Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                // Status row
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(14.dp),
                    Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape)
                        .background(if (pinSet) Green.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                        Alignment.Center) {
                        Icon(Icons.Default.Lock, null, Modifier.size(20.dp),
                            tint = if (pinSet) Green else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (pinSet) "PIN Lock Enabled" else "PIN Lock Disabled",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (pinSet) Green else MaterialTheme.colorScheme.onSurface
                            ))
                        Text(
                            if (pinSet) "App locks when you switch away"
                            else "Tap Set PIN to secure the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Buttons row below
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    if (pinSet) {
                        OutlinedButton(
                            onClick = { pinManager.clearPin(); pinSet = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                        ) { Text("Remove PIN") }
                    }
                    Button(
                        onClick = { showPinSetup = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(if (pinSet) "Change PIN" else "Set PIN") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // â”€â”€ App Info â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        SettingsSectionLabel("App Info")
        SettingsCard {
            SettingsActionRow(
                icon     = Icons.Default.BugReport,
                color    = Red,
                title    = "Report a Bug",
                subtitle = "Send feedback via WhatsApp or Email"
            ) {
                val deviceInfo = buildString {
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
                    appendLine("App Version: ${app.fynlo.BuildConfig.VERSION_NAME} (${app.fynlo.BuildConfig.VERSION_CODE})")
                    appendLine("\nDescribe your issue:\n[Type here]")
                }
                try {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://wa.me/918500504810?text=${android.net.Uri.encode(deviceInfo)}")
                    ))
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:avinash999.reddy@gmail.com")
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Fynlo Bug Report")
                        putExtra(android.content.Intent.EXTRA_TEXT, deviceInfo)
                    })
                }
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

        // â”€â”€ Developer (debug only) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (app.fynlo.BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            SettingsSectionLabel("Developer")
            SettingsCard {
                var showSeedConfirm    by remember { mutableStateOf(false) }
                var showCleanupConfirm by remember { mutableStateOf(false) }
                var showRestoreConfirm by remember { mutableStateOf(false) }

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

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsActionRow(Icons.Default.Science, Amber, "Load Test Data (QA)",
                        "Seeds fake data for testing") { showSeedConfirm = true }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.CleaningServices, Red, "Cleanup Seeder Data",
                        "Remove QA data from app + Firestore") { showCleanupConfirm = true }
                    SettingsDivider()
                    SettingsActionRow(Icons.Default.Restore, Blue, "Restore Real Data",
                        "Reset to real account balances") { showRestoreConfirm = true }
                }
            }
        }

        // â”€â”€ Version â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

// â”€â”€ Shared composables â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun SettingsSectionLabel(title: String) {
    Text(
        title.uppercase(),
        style    = MaterialTheme.typography.labelSmall.copy(
            fontWeight    = FontWeight.Bold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f,
                androidx.compose.ui.unit.TextUnitType.Sp)
        ),
        color    = Green,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
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
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(0.12f)),
            Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = color)
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

