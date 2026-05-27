package app.fynlo.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import app.fynlo.data.UserPreferences
import app.fynlo.ui.theme.Emerald400
import app.fynlo.ui.theme.Emerald500
import app.fynlo.ui.theme.Emerald700
import app.fynlo.ui.theme.Emerald900
import app.fynlo.ui.theme.ThemeController

// 3.2.21 — was 3 steps (theme / notifications / profile). The theme step is
// removed per UX feedback: forcing the user to pick light/dark mode before
// they've even used the app is a friction point; the app defaults to
// "Follow system" (`darkMode(): "system"` in UserPreferences) which is the
// right choice for almost everyone. Users who want to override can do so
// from Settings → Personalization any time. Now 2 steps: notifications + profile.
private const val TOTAL_STEPS = 2

@Composable
fun FirstLaunchSetupScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) }

    // Step 1 state — reminders (was step 2 before the 3.2.21 theme-step removal)
    var notificationsEnabled by remember { mutableStateOf(true) }

    // Step 2 state — name (was step 3)
    var displayName by remember { mutableStateOf("") }

    // Notification permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled — user sees system dialog */ }

    // 3.2.21 — was hardcoded `Brush.verticalGradient(Emerald900, Emerald700)`
    // — a dark "premium splash" look regardless of system theme. Per user
    // feedback, the setup pages should respect the phone's light/dark
    // setting. Now using the same theme-aware gradient as OnboardingScreen:
    // bg → bg → faint emerald tint. Reads light on light theme, dark on
    // dark theme. The emerald accent at the bottom keeps brand identity
    // in both modes (it's a 4% alpha so subtle in both).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        Emerald700.copy(alpha = 0.04f),
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Skip on last step
            if (currentStep == TOTAL_STEPS - 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        scope.launch { UserPreferences.setUserDisplayName(context, "") }
                        app.fynlo.data.Analytics.setupSkipped(atStep = currentStep)
                        onComplete()
                    }) {
                        // 3.2.21 — theme-aware text colour (was Color.White)
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Animated step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn())
                            .togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn())
                            .togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                },
                modifier = Modifier.weight(1f),
                label = "step"
            ) { step ->
                // 3.2.21 — was 3 cases (0=theme, 1=notifications, 2=profile).
                // Theme step dropped; remaining steps re-indexed to 0/1.
                when (step) {
                    0 -> NotificationStep(
                        enabled = notificationsEnabled,
                        onSelect = { notificationsEnabled = it }
                    )
                    1 -> ProfileStep(
                        name = displayName,
                        onNameChange = { displayName = it }
                    )
                }
            }

            // Progress dots
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                repeat(TOTAL_STEPS) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == currentStep) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                // 3.2.21 — theme-aware inactive dot colour
                                // (was Color.White.copy(alpha=0.3f), unreadable
                                // on a light background).
                                if (index == currentStep) Emerald500
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (currentStep > 0) {
                    // 3.2.21 — theme-aware background + icon tint.
                    IconButton(
                        onClick = { currentStep-- },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }

                // Next / Get Started button
                val isLast = currentStep == TOTAL_STEPS - 1
                Button(
                    onClick = {
                        // 3.2.21 — step indices shifted after the theme step
                        // removal (was 0=theme/1=notifications/2=profile).
                        val stepName = when (currentStep) {
                            0 -> "notifications"; 1 -> "profile"; else -> "unknown"
                        }
                        when (currentStep) {
                            0 -> {
                                scope.launch { UserPreferences.setNotificationsEnabled(context, notificationsEnabled) }
                                if (notificationsEnabled &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ) {
                                    if (ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                }
                            }
                            1 -> {
                                scope.launch { UserPreferences.setUserDisplayName(context, displayName.trim()) }
                            }
                        }
                        app.fynlo.data.Analytics.setupStepComplete(step = currentStep + 1, stepName = stepName)
                        if (isLast) {
                            app.fynlo.data.Analytics.setupComplete()
                            onComplete()
                        } else {
                            currentStep++
                        }
                    },
                    // 3.2.21 — was white-on-emerald-900 which only made sense
                    // against the old dark gradient. Now uses the brand
                    // emerald accent (works against both light and dark
                    // backgrounds) with white text.
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald500,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        if (isLast) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLast) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Step 1: Theme ────────────────────────────────────────────────────────────

@Composable
private fun ThemeStep(selected: Boolean?, onSelect: (Boolean?) -> Unit) {
    StepLayout(
        icon = Icons.Default.DarkMode,
        title = "Pick Your Theme",
        subtitle = "Choose how Fynlo looks"
    ) {
        val options = listOf(
            Triple(false, Icons.Default.LightMode, "Light Mode"),
            Triple(true, Icons.Default.DarkMode, "Dark Mode"),
            Triple<Boolean?, ImageVector, String>(null, Icons.Default.PhoneAndroid, "System Default")
        )
        options.forEach { (value, icon, label) ->
            SelectionCard(
                selected = selected == value,
                onClick = { onSelect(value) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(label, fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (selected == value) {
                        Icon(
                            Icons.Default.Check, null,
                            tint = Emerald400, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Step 3: Notifications ────────────────────────────────────────────────────

@Composable
private fun NotificationStep(enabled: Boolean, onSelect: (Boolean) -> Unit) {
    // 3.2.21 — all hardcoded Color.White / Color.White.copy(alpha=0.6f) →
    // theme-aware onSurface / onSurfaceVariant. Emerald checkmark stays.
    StepLayout(
        icon = Icons.Default.Notifications,
        title = "Stay on Top of Your Money",
        subtitle = "Never miss a payment or deadline"
    ) {
        SelectionCard(
            selected = enabled,
            onClick = { onSelect(true) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Notifications, null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Reminders", fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Get alerts for overdue loans and upcoming payments",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (enabled) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = Emerald500, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        SelectionCard(
            selected = !enabled,
            onClick = { onSelect(false) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.NotificationsOff, null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Not Now", fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "You can always enable this later in Settings",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!enabled) {
                    Icon(
                        Icons.Default.Check, null,
                        tint = Emerald500, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ── Step 4: Profile ──────────────────────────────────────────────────────────

@Composable
private fun ProfileStep(name: String, onNameChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    StepLayout(
        icon = Icons.Default.Person,
        title = "What Should We Call You?",
        subtitle = "Used for greetings only"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Your Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            // 3.2.21 — theme-aware colors. Emerald accents (cursor, focused
            // border/label) stay branded; text and unfocused states migrate
            // to MaterialTheme so they read on both light and dark bg.
            colors = OutlinedTextFieldDefaults.colors(
                cursorColor = Emerald500,
                focusedBorderColor = Emerald500,
                focusedLabelColor = Emerald500,
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Optional",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── Shared layout for each step ──────────────────────────────────────────────

@Composable
private fun StepLayout(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // 3.2.21 — large-icon container theme-aware; brand emerald accent stays.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Emerald500.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(36.dp), tint = Emerald500)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        content()

        Spacer(Modifier.weight(1f))
    }
}

// ── Reusable selection card ──────────────────────────────────────────────────

@Composable
private fun SelectionCard(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // 3.2.21 — theme-aware border + background. Selected state keeps emerald
    // accent (brand). Unselected falls back to surfaceVariant so the card is
    // visible on light backgrounds (the prior `Color.White.copy(alpha=0.06f)`
    // disappeared into a light bg).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) Modifier.border(
                    2.dp, Emerald500, RoundedCornerShape(14.dp)
                ) else Modifier.border(
                    1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)
                )
            )
            .background(
                if (selected) Emerald500.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable(onClick = onClick)
    ) {
        content()
    }
}
