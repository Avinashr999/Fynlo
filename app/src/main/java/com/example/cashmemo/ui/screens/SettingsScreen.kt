package com.example.cashmemo.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.FinanceViewModel
import com.example.cashmemo.ui.theme.ThemeController
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: FinanceViewModel, onNavigateToAbout: () -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings & Data",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ── Theme toggle ──────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ThemeController.darkModeOverride == null,  onClick = { ThemeController.darkModeOverride = null  }, label = { Text("System") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = ThemeController.darkModeOverride == false, onClick = { ThemeController.darkModeOverride = false }, label = { Text("Light")  }, modifier = Modifier.weight(1f))
                    FilterChip(selected = ThemeController.darkModeOverride == true,  onClick = { ThemeController.darkModeOverride = true  }, label = { Text("Dark")   }, modifier = Modifier.weight(1f))
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
                Text("Export Excel (CSV)")
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

        Spacer(modifier = Modifier.height(32.dp))

        Text("App Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        
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

        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "Version ${com.example.cashmemo.BuildConfig.VERSION_NAME}",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
