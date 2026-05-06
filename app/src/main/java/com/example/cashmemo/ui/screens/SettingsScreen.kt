package com.example.cashmemo.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.ui.theme.ThemeController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: FinanceViewModel, onNavigateToAbout: () -> Unit, onNavigateToProfile: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isExporting = true
                try {
                    val jsonData = viewModel.exportAllData()
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(jsonData.toByteArray())
                    }
                    // Show success snackbar if we had a scaffold state, 
                    // for now we'll just stop the loading state
                } catch (e: Exception) {
                    // Handle error
                } finally {
                    isExporting = false
                }
            }
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings & Data",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // â”€â”€ Theme toggle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ThemeController.darkModeOverride == null,  onClick = { ThemeController.darkModeOverride = null;  ThemeController.save(context) }, label = { Text("System") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = ThemeController.darkModeOverride == false, onClick = { ThemeController.darkModeOverride = false; ThemeController.save(context) }, label = { Text("Light")  }, modifier = Modifier.weight(1f))
                    FilterChip(selected = ThemeController.darkModeOverride == true,  onClick = { ThemeController.darkModeOverride = true;  ThemeController.save(context) }, label = { Text("Dark")   }, modifier = Modifier.weight(1f))
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Auto-Backup is Active", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Your data is automatically synced to Google Drive.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Manual Backup & Exports", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
            "Export your data in different formats for taxes or record keeping.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val createCSVLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv")
        ) { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(viewModel.exportToCSV().toByteArray())
                }
            }
        }

        val createPDFLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf")
        ) { uri ->
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    viewModel.exportToPDF(os)
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                scope.launch {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val json = inputStream.bufferedReader().use { it.readText() }
                        viewModel.restoreData(json)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { createDocumentLauncher.launch("CashMemo_Backup_${System.currentTimeMillis()}.json") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export JSON (System Backup)")
            }

            Button(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Restore Data (Import JSON)")
            }

            OutlinedButton(
                onClick = { createCSVLauncher.launch("CashMemo_Expenses_${System.currentTimeMillis()}.csv") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Expenses (CSV)")
            }

            // Full Excel backup
            val xlsxLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/comma-separated-values")
            ) { uri ->
                uri?.let {
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(it)?.use { os ->
                                val accounts     = viewModel.accounts.value
                                val transactions = viewModel.transactions.value
                                val borrowers    = viewModel.borrowers.value
                                val debts        = viewModel.debts.value
                                val investments  = viewModel.investments.value
                                val payments     = viewModel.payments.value
                                val debtPayments = viewModel.debtPayments.value

                                val sb = StringBuilder()
                                // Accounts
                                sb.appendLine("=== ACCOUNTS ===")
                                sb.appendLine("Name,Balance,Type")
                                accounts.forEach { a -> sb.appendLine("\"${a.name}\",${a.balance},\"${a.type}\"") }
                                sb.appendLine()

                                // Transactions
                                sb.appendLine("=== TRANSACTIONS ===")
                                sb.appendLine("Date,Type,Amount,From,To,Category,Description,Notes")
                                transactions.sortedByDescending { it.date }.forEach { t ->
                                    sb.appendLine("\"${t.date}\",\"${t.type}\",${t.amount},\"${t.fromAcct}\",\"${t.toAcct}\",\"${t.category}\",\"${t.desc}\",\"${t.notes}\"")
                                }
                                sb.appendLine()

                                // Borrowers
                                sb.appendLine("=== LENDING ===")
                                sb.appendLine("Name,Phone,Principal,Rate,Interest Type,Loan Date,Due Date,Paid,Status")
                                borrowers.forEach { b ->
                                    sb.appendLine("\"${b.name}\",\"${b.phone}\",${b.amount},${b.rate},\"${b.type}\",\"${b.date}\",\"${b.due}\",${b.paid},\"${b.status}\"")
                                }
                                sb.appendLine()

                                // Debts
                                sb.appendLine("=== DEBTS ===")
                                sb.appendLine("Name,Principal,Rate,Interest Type,Date,Due Date,Paid,Notes")
                                debts.forEach { d ->
                                    sb.appendLine("\"${d.name}\",${d.amount},${d.rate},\"${d.intType}\",\"${d.date}\",\"${d.due}\",${d.paid},\"${d.notes}\"")
                                }
                                sb.appendLine()

                                // Investments
                                sb.appendLine("=== INVESTMENTS ===")
                                sb.appendLine("Name,Type,Invested,Current Value,Growth,Date")
                                investments.forEach { i ->
                                    sb.appendLine("\"${i.name}\",\"${i.type}\",${i.invested},${i.currentVal},${i.currentVal - i.invested},\"${i.date}\"")
                                }
                                sb.appendLine()

                                // Payments
                                sb.appendLine("=== LOAN REPAYMENTS ===")
                                sb.appendLine("Borrower,Amount,Date,Notes")
                                payments.forEach { p ->
                                    sb.appendLine("\"${p.name}\",${p.amount},\"${p.date}\",\"${p.notes}\"")
                                }
                                sb.appendLine()

                                // Debt Payments
                                sb.appendLine("=== DEBT REPAYMENTS ===")
                                sb.appendLine("Creditor,Amount,Date,Notes")
                                debtPayments.forEach { p ->
                                    sb.appendLine("\"${p.name}\",${p.amount},\"${p.date}\",\"${p.notes}\"")
                                }

                                os.write(sb.toString().toByteArray(Charsets.UTF_8))
                            }
                        }.onFailure { e ->
                            android.util.Log.e("ExcelExport", "Export failed", e)
                        }
                    }
                }
            }
            Button(
                onClick = { xlsxLauncher.launch("CashMemo_FullBackup_${System.currentTimeMillis()}.csv") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = MaterialTheme.shapes.medium,
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Icon(Icons.Default.GridOn, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export Full Backup (.csv)")
            }

            OutlinedButton(
                onClick = { createPDFLauncher.launch("CashMemo_Report_${System.currentTimeMillis()}.pdf") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Export PDF Report")
            }
        }

        
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Notifications", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                com.example.cashmemo.notifications.ReminderScheduler.schedule(context)
                android.widget.Toast.makeText(context, "Reminders scheduled! Due date alerts will fire daily.", android.widget.Toast.LENGTH_LONG).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Notifications, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Test Notifications")
        }
        Text("Schedules daily reminders for loan due dates and budget alerts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Security", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        val pinManager = remember { com.example.cashmemo.data.PinManager(context) }
        var pinSet by remember { mutableStateOf(pinManager.isPinSet) }
        var showPinSetup by remember { mutableStateOf(false) }
        if (showPinSetup) {
            com.example.cashmemo.ui.screens.PinScreen(
                mode      = com.example.cashmemo.ui.screens.PinMode.SET,
                onSuccess = { pinSet = pinManager.isPinSet; showPinSetup = false },
                onSkip    = { showPinSetup = false }
            )
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(if (pinSet) "PIN Lock Enabled" else "PIN Lock Disabled",
                        style = MaterialTheme.typography.bodyLarge)
                    Text(if (pinSet) "App locks when you switch away" else "Set a 4-digit PIN to secure the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pinSet) {
                        OutlinedButton(onClick = { pinManager.clearPin(); pinSet = false },
                            shape = RoundedCornerShape(8.dp)) { Text("Remove") }
                    }
                    Button(onClick = { showPinSetup = true },
                        shape = RoundedCornerShape(8.dp)) {
                        Text(if (pinSet) "Change" else "Set PIN")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("App Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

        // ── Developer / Test ──────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Developer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        var showSeedConfirm by remember { mutableStateOf(false) }
        if (showSeedConfirm) {
            AlertDialog(
                onDismissRequest = { showSeedConfirm = false },
                title = { Text("Load Test Data?") },
                text  = { Text("This will add dummy accounts, loans, debts, investments and transactions for testing. Existing data will not be deleted.") },
                confirmButton = { Button(onClick = { viewModel.loadDummyData(); showSeedConfirm = false }) { Text("Load") } },
                dismissButton = { TextButton(onClick = { showSeedConfirm = false }) { Text("Cancel") } }
            )
        }
        OutlinedButton(
            onClick  = { showSeedConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Science, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Load Test Data (QA)")
        }
        
        TextButton(
            onClick = onNavigateToAbout,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("About & Disclaimer", style = MaterialTheme.typography.bodyLarge)
                    Text("Legal information and app details", style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "Version ${com.example.cashmemo.BuildConfig.VERSION_NAME}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


