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


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BackupPasswordDialog(
    mode: BackupPasswordMode,
    errorMessage: String?= null,
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

