package app.fynlo.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val TemplateScreenPadding = 18.dp
val TemplateCardRadius = 16.dp
val TemplatePanelRadius = 24.dp
val TemplateHeroRadius = 26.dp
val TemplateMotionDurationMs = 220
val TemplateBorder = Color(0xFFDCE8E0)
val TemplateMutedText = Carbon600
val TemplateCanvas = LightBackground
val TemplateAction = Emerald500
val TemplateActionDark = Emerald700

@Composable
fun PremiumSoftGlassPanel(
    modifier: Modifier = Modifier,
    radius: Dp = TemplatePanelRadius,
    borderAlpha: Float = 0.48f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha),
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun PremiumSkeletonBlock(
    modifier: Modifier = Modifier.fillMaxWidth(),
    height: Dp,
    radius: Dp = TemplateCardRadius,
) {
    val transition = rememberInfiniteTransition(label = "premiumSkeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "premiumSkeletonPulse",
    )
    Surface(
        modifier = modifier
            .height(height)
            .alpha(pulse),
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
        ),
    ) {}
}

@Composable
fun PremiumSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    showAmount: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    0.7.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    PremiumSkeletonBlock(
                        modifier = Modifier.size(38.dp),
                        height = 38.dp,
                        radius = 19.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        PremiumSkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.72f),
                            height = 15.dp,
                            radius = 8.dp,
                        )
                        PremiumSkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.48f),
                            height = 11.dp,
                            radius = 8.dp,
                        )
                        PremiumSkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.62f),
                            height = 10.dp,
                            radius = 8.dp,
                        )
                    }
                    if (showAmount) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            PremiumSkeletonBlock(
                                modifier = Modifier.width(82.dp),
                                height = 14.dp,
                                radius = 8.dp,
                            )
                            PremiumSkeletonBlock(
                                modifier = Modifier.width(48.dp),
                                height = 10.dp,
                                radius = 8.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Fynlo Premium Design System ───────────────────────────────────────────────
// Deep Emerald + Carbon — inspired by premium fintech apps
// All screens use these shared components for consistency

// ── Screen header — flat title on background (matches dashboard) ───────────────
@Composable
fun PremiumScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    action: (@Composable () -> Unit)?= null,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(start = TemplateScreenPadding, end = TemplateScreenPadding, top = 14.dp, bottom = 6.dp),
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

@Composable
fun LedgerDetailTopBar(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String?= null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = onNavigateBack,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

// ── Section label — emerald accent with dot ───────────────────────────────────
@Composable
fun LedgerTopBarActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        border = BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Composable
fun DetailActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    warning: Boolean = false,
) {
    val contentColor = when {
        destructive -> MaterialTheme.colorScheme.error
        warning -> SemanticAmber
        else -> Emerald500
    }
    val background = if (emphasized) contentColor else MaterialTheme.colorScheme.surface
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = background,
        tonalElevation = if (emphasized) 0.dp else 1.dp,
        border = if (emphasized) null else BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (emphasized) Color.White else contentColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = if (emphasized) Color.White else contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        )
    }
}

// ── Premium stat card — metric display ────────────────────────────────────────
@Composable
fun PremiumStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector?= null,
    iconTint: Color = Emerald500,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)?= null
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
    borderColor: Color = TemplateBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(0.7.dp, borderColor.copy(alpha = 0.82f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

// ── Settings item row — for settings screen ───────────────────────────────────
@Composable
fun PremiumSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    iconBg: Color = Emerald500,
    trailing: (@Composable () -> Unit)?= null,
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

@Composable
fun LedgerPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
fun LedgerSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    count: String?= null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.primary,
        )
        if (count != null) {
            Text(
                count,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
fun LedgerMetricBand(
    metrics: List<LedgerMetric>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        metrics.forEachIndexed { index, metric ->
            LedgerMetricCard(
                metric = metric,
                modifier = Modifier.weight(if (index == 0) 1.25f else 1f),
            )
        }
    }
}

data class LedgerMetric(
    val label: String,
    val value: String,
    val valueColor: Color?= null,
)

@Composable
fun LedgerMetricCard(
    metric: LedgerMetric,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 78.dp),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                metric.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                metric.value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = metric.valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LedgerHeroPanel(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Emerald700,
    supporting: (@Composable ColumnScope.() -> Unit)?= null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateHeroRadius),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            containerColor,
                            containerColor.copy(alpha = 0.92f),
                            Emerald500.copy(alpha = 0.86f),
                        ),
                    ),
                )
                .padding(18.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                value,
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 2,
            )
            if (supporting != null) {
                Spacer(Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    content = supporting,
                )
            }
        }
    }
}

@Composable
fun LedgerIconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
    }
}

@Composable
fun LedgerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Emerald500,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(TemplateCardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(
            0.7.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LedgerIconTile(icon = icon, tint = iconTint)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = valueColor,
                    maxLines = 1,
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

// ── C06: shared FAB clear-zone constant ───────────────────────────────────────
// Every scrollable container (LazyColumn `contentPadding.bottom`, or a trailing
// Spacer inside a verticalScroll Column) that sits underneath a FAB should
// reserve this much space at the bottom so the last list item doesn't render
// Bottom scroll clearance for screens inside the main app chrome.
// The global Quick Add FAB is removed, and the bottom nav lives outside the
// scroll area, so this should prevent clipped last rows without creating a
// large empty band at the bottom of every screen.
//
// Apply at the call site as either:
//   LazyColumn(contentPadding = PaddingValues(bottom = FabBottomPadding))
//   Spacer(Modifier.height(FabBottomPadding))   // inside verticalScroll Column
val FabBottomPadding = 72.dp

@Composable
fun TemplatePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector?= null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TemplateAction,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TemplateSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TemplatePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) TemplateAction else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(0.8.dp, TemplateBorder),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TemplateSegmentedSelector(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            TemplatePill(
                text = option,
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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
