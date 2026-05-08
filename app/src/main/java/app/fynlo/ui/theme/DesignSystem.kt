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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Design Tokens ──────────────────────────────────────────────────────────────

object AppRadius {
    val small   = RoundedCornerShape(8.dp)    // chips, badges, tags
    val medium  = RoundedCornerShape(12.dp)   // buttons, input fields, small cards
    val large   = RoundedCornerShape(16.dp)   // standard cards
    val hero    = RoundedCornerShape(20.dp)   // hero/summary cards
    val circle  = CircleShape
}

object AppColor {
    val green        = Color(0xFF059669)
    val greenLight   = Color(0xFF059669).copy(alpha = 0.1f)
    val red          = Color(0xFFEF4444)
    val redLight     = Color(0xFFEF4444).copy(alpha = 0.1f)
    val blue         = Color(0xFF3B82F6)
    val blueLight    = Color(0xFF3B82F6).copy(alpha = 0.1f)
    val amber        = Color(0xFFF59E0B)
    val amberLight   = Color(0xFFF59E0B).copy(alpha = 0.1f)
    val purple       = Color(0xFF8B5CF6)
    val purpleLight  = Color(0xFF8B5CF6).copy(alpha = 0.1f)
    val teal         = Color(0xFF06B6D4)
}

object AppSpacing {
    val xs   = 4.dp
    val sm   = 8.dp
    val md   = 12.dp
    val lg   = 16.dp
    val xl   = 20.dp
    val xxl  = 24.dp
    val screenPad = 16.dp
    val cardPad   = 16.dp
}

// ── Reusable Composables ───────────────────────────────────────────────────────

/** Standard card used everywhere in the app */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shape: RoundedCornerShape = AppRadius.large,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = shape,
        colors   = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.cardPad),
            content  = content
        )
    }
}

/** Colored metric/summary card */
@Composable
fun MetricCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null) modifier.fillMaxWidth()
              else modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) mod.then(Modifier) else mod,
        shape    = AppRadius.large,
        colors   = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        onClick  = onClick ?: {}
    ) {
        Column(Modifier.padding(AppSpacing.cardPad)) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor)
            Spacer(Modifier.height(AppSpacing.xs))
            Text(value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
        }
    }
}

/** Hero summary card (net worth, totals) */
@Composable
fun HeroCard(
    title: String,
    value: String,
    subtitle: String? = null,
    accentColor: Color = AppColor.green,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = AppRadius.hero,
        colors   = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        onClick  = onClick ?: {}
    ) {
        Column(Modifier.padding(AppSpacing.xl)) {
            Text(title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(AppSpacing.xs))
            Text(value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = accentColor)
            if (subtitle != null) {
                Spacer(Modifier.height(AppSpacing.xs))
                Text(subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Consistent section header row */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier              = modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        action?.invoke()
    }
}

/** Consistent empty state */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier            = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(horizontal = 32.dp))
        action?.invoke()
    }
}

/** Icon+label quick action button */
@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        FilledTonalButton(
            onClick       = onClick,
            modifier      = Modifier.size(52.dp),
            shape         = AppRadius.medium,
            colors        = ButtonDefaults.filledTonalButtonColors(
                containerColor = color.copy(alpha = 0.12f)
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = color)
        }
        Text(label,
            style   = MaterialTheme.typography.labelSmall,
            color   = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center)
    }
}

/** Status badge (OVERDUE, SETTLED, etc.) */
@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color    = color.copy(alpha = 0.12f),
        shape    = AppRadius.small
    ) {
        Text(
            text     = text,
            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color    = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Divider row used in data tables inside cards */
@Composable
fun DataRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (valueColor == Color.Unspecified)
                MaterialTheme.colorScheme.onSurface else valueColor)
    }
}
