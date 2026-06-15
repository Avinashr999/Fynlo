package app.fynlo.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.BuildConfig
import app.fynlo.ui.theme.Emerald500
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.UUID

/**
 * C18 #4 (3.2.78) — in-app bug report form.
 *
 * Replaces the email-only Settings entry with a structured form:
 *   - Title + Description fields (both required)
 *   - "Include device info" toggle (default ON) — captures
 *     manufacturer / model / Android version / app version / build flavor
 *   - Generated short report ID (8-char) so support can correlate the
 *     in-app submission with whatever the user emails / messages
 *
 * On submit:
 *   - Pushes a structured `recordException` + custom keys to Firebase
 *     Crashlytics as a NON-FATAL event. Keys: report_id, title,
 *     description, device_info, app_version. Crashlytics surfaces these
 *     in the "non-fatals" tab without flagging the build as crashy.
 *   - Offers an "Email a copy" fallback button so users on devices
 *     without Crashlytics access (no Play Services, regional restrictions)
 *     can still escalate.
 *
 * No new dependencies — Crashlytics is already in the BoM.
 */
@Composable
fun ReportBugDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title           by remember { mutableStateOf("") }
    var description     by remember { mutableStateOf("") }
    var includeDeviceInfo by remember { mutableStateOf(true) }
    // Stable per-dialog-open report ID so re-render doesn't shift it.
    val reportId        = remember { UUID.randomUUID().toString().take(8).uppercase() }
    var submitState     by remember { mutableStateOf<SubmitState>(SubmitState.Idle) }

    val canSubmit = title.isNotBlank() && description.isNotBlank() && submitState !is SubmitState.Sending

    FormDialog(title = "Report a bug", onDismiss = onDismiss) {
        Text(
            "Tell us what went wrong. Your message goes to our crash-reporting service " +
            "(no other personal data leaves your device beyond what you write here).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        FormSectionLabel("Title")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = title, onValueChange = { title = it },
            placeholder = { Text("Short summary, e.g. 'Net worth wrong after sync'") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(12.dp))
        FormSectionLabel("Description")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            placeholder = { Text("What did you do, what did you expect, what happened?") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includeDeviceInfo, onCheckedChange = { includeDeviceInfo = it })
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Include device + app info", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Phone model, Android version, app version. Helps reproduce.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "Report ID: $reportId · ${if (submitState is SubmitState.Sent) "Sent" else "Not sent yet"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // Status / result line.
        when (val s = submitState) {
            is SubmitState.Idle    -> { /* no banner */ }
            is SubmitState.Sending -> {
                Spacer(Modifier.height(8.dp))
                Text("Sending…", style = MaterialTheme.typography.labelSmall, color = Emerald500)
            }
            is SubmitState.Sent    -> {
                Spacer(Modifier.height(8.dp))
                Text("Report sent. Reference ID: $reportId",
                    style = MaterialTheme.typography.labelSmall, color = Emerald500)
            }
            is SubmitState.Error   -> {
                Spacer(Modifier.height(8.dp))
                Text("Couldn't send via Crashlytics (${s.reason}). Use Email a copy below.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                submitState = SubmitState.Sending
                try {
                    sendToCrashlytics(reportId, title, description, includeDeviceInfo)
                    submitState = SubmitState.Sent
                } catch (e: Throwable) {
                    submitState = SubmitState.Error(e.message ?: e::class.java.simpleName)
                }
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
        ) {
            Text(
                if (submitState is SubmitState.Sent) "Sent" else "Send report",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                emailFallback(context, reportId, title, description, includeDeviceInfo)
            },
            enabled = title.isNotBlank() && description.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Email a copy")
        }
    }
}

private sealed class SubmitState {
    data object Idle    : SubmitState()
    data object Sending : SubmitState()
    data object Sent    : SubmitState()
    data class Error(val reason: String) : SubmitState()
}

private fun deviceInfoBlock(): String = buildString {
    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.FLAVOR}/${BuildConfig.BUILD_TYPE}")
}

private fun sendToCrashlytics(
    reportId: String,
    title: String,
    description: String,
    includeDeviceInfo: Boolean,
) {
    val crashlytics = FirebaseCrashlytics.getInstance()
    crashlytics.setCustomKey("report_id", reportId)
    crashlytics.setCustomKey("bug_title", title.take(120))
    if (includeDeviceInfo) {
        crashlytics.setCustomKey("device_info", deviceInfoBlock())
    }
    // recordException routes this to the "Non-fatals" tab in the Firebase
    // console — distinct from real crashes so build health metrics stay
    // clean. The exception payload carries the description as the message.
    crashlytics.recordException(BugReport(title = title, description = description, reportId = reportId))
}

/**
 * Non-fatal carrier for Crashlytics. Subclassing Exception (rather than
 * RuntimeException) signals "this is informational, not a code path
 * indicating broken state" — same convention `FirebaseCrashlytics` uses
 * for its own custom logs.
 */
private class BugReport(title: String, description: String, reportId: String) :
    Exception("[$reportId] $title — $description")

private fun emailFallback(
    context: Context,
    reportId: String,
    title: String,
    description: String,
    includeDeviceInfo: Boolean,
) {
    val body = buildString {
        appendLine("Report ID: $reportId")
        appendLine("Title: $title")
        appendLine()
        appendLine(description)
        appendLine()
        if (includeDeviceInfo) {
            appendLine("--")
            append(deviceInfoBlock())
        }
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:fynloapp.support@gmail.com")
        putExtra(Intent.EXTRA_SUBJECT, "Fynlo Bug Report [$reportId] $title")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }
}
