package app.fynlo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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

// ── Fynlo Premium Design System ───────────────────────────────────────────────
// Deep Emerald + Carbon — inspired by premium fintech apps
// All screens use these shared components for consistency

// ── Screen header — flat title on background (matches dashboard) ───────────────
@Composable
fun PremiumScreenHeader(
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val base = if (onClick != null)
        modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    else modifier
    Column(base.padding(vertical = 6.dp, horizontal = 2.dp)) {
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

// ── Premium list card — wraps content in consistent card style ─────────────────
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
        content = content
    )
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
            trailing?.invoke() ?: Text("›", 
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
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

// ── C06: shared FAB clear-zone constant ───────────────────────────────────────
// Every scrollable container (LazyColumn `contentPadding.bottom`, or a trailing
// Spacer inside a verticalScroll Column) that sits underneath a FAB should
// reserve this much space at the bottom so the last list item doesn't render
// under the FAB.
//
// Math: M3 FloatingActionButton is 56dp tall + 16dp of container margin =
// 72dp minimum. We use 120dp to leave a comfortable ~48dp safety margin for
// systems that add extra inset (gesture nav bar, predictive back hint) and
// for visual breathing room on the last row. Per `DESIGN_SYSTEM.md §5.2`
// (which prescribed 96dp); the survey of existing screens used 100dp and the
// user still reported overlap, so 120dp is the floor.
//
// Apply at the call site as either:
//   LazyColumn(contentPadding = PaddingValues(bottom = FabBottomPadding))
//   Spacer(Modifier.height(FabBottomPadding))   // inside verticalScroll Column
val FabBottomPadding = 160.dp

// ── C07: shared empty-state — single CTA, no duplicate FAB ────────────────────
// Audit (UX_AUDIT §C07) fix point #1: "Empty state shows ONLY the 'Add First X'
// CTA per DESIGN_SYSTEM.md §9.6." Use this on any screen where the empty
// state historically rendered both a FAB and an inline CTA simultaneously
// (Goals, Budgets, Recurring). The screen-level FAB / header `+` should be
// hidden when the list is empty so this composable's [actionLabel] button
// is the single unambiguous entry point.
//
// Parameters:
//   icon         — leading icon (typically a domain-relevant `Icons.Default.*`)
//   title        — primary line ("No goals yet")
//   subtitle     — secondary line explaining purpose ("Set a savings target …")
//   actionLabel  — CTA pill label ("Add First Goal" / "Add First Budget" / …)
//   onAction     — invoked when the user taps the CTA pill
//   modifier     — optional outer Modifier (caller usually wraps in a centred Box)
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            FilledTonalButton(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}
