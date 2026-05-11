package app.fynlo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

// ── Fynlo Premium Design System ───────────────────────────────────────────────
// Deep Emerald + Carbon — inspired by premium fintech apps
// All screens use these shared components for consistency

// ── Screen header — used at top of every screen ───────────────────────────────
@Composable
fun PremiumScreenHeader(
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Emerald500
                )
            }
        }
        action?.invoke()
    }
}

// ── Section label — emerald accent with dot ───────────────────────────────────
@Composable
fun PremiumSectionLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(4.dp).clip(CircleShape).background(Emerald500)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = Emerald500
            )
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = Emerald500.copy(alpha = 0.2f)
        )
    }
}

// ── Premium stat card — metric display ────────────────────────────────────────
@Composable
fun PremiumStatCard(
    label: String,
    value: String,
    icon: ImageVector? = null,
    iconTint: Color = Emerald500,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardMod = if (onClick != null) modifier.then(Modifier.padding(0.dp)) else modifier
    Surface(
        modifier = cardMod,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        onClick = onClick ?: {}
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(14.dp), tint = iconTint)
                } else {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(iconTint))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = valueColor
                )
            )
        }
    }
}

// ── Premium list card — wraps content in consistent card style ─────────────────
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (borderColor != Color.Transparent) borderColor
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        )
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

// ── Settings item row — for settings screen ───────────────────────────────────
@Composable
fun PremiumSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    iconBg: Color = Emerald500,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconBg.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(19.dp), tint = iconBg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing?.invoke() ?: Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForwardIos, null,
                Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Section divider with label ─────────────────────────────────────────────────
@Composable
fun PremiumSectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
    )
}

// ── Amount display helper ──────────────────────────────────────────────────────
fun formatAmount(amount: Double, symbol: String = "₹"): String {
    val locale = Locale.getDefault()
    return "$symbol ${String.format(locale, "%,.0f", amount)}"
}
