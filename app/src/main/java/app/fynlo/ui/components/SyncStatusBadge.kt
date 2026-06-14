package app.fynlo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.fynlo.data.SyncStatus
import app.fynlo.ui.theme.*

@Composable
fun SyncStatusBadge(status: SyncStatus, modifier: Modifier = Modifier) {
    when (status) {
        is SyncStatus.Synced -> Icon(
            imageVector        = Icons.Default.CloudDone,
            contentDescription = "Synced",
            tint               = Emerald500,
            modifier           = modifier.size(22.dp)
        )
        is SyncStatus.Syncing -> {
            val inf = rememberInfiniteTransition(label = "sync_spin")
            val angle by inf.animateFloat(
                initialValue   = 0f,
                targetValue    = 360f,
                animationSpec  = infiniteRepeatable(tween(900, easing = LinearEasing)),
                label          = "spin"
            )
            Icon(
                imageVector        = Icons.Default.Sync,
                contentDescription = "Syncing",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = modifier.size(22.dp).rotate(angle)
            )
        }
        is SyncStatus.Offline -> Icon(
            imageVector        = Icons.Default.CloudOff,
            contentDescription = "Offline",
            tint               = SemanticAmber,
            modifier           = modifier.size(22.dp)
        )
        is SyncStatus.Initialising -> Icon(
            imageVector        = Icons.Default.Cloud,
            contentDescription = "Connecting",
            tint               = MaterialTheme.colorScheme.outlineVariant,
            modifier           = modifier.size(22.dp)
        )
        is SyncStatus.Error -> Icon(
            imageVector        = Icons.Default.Warning,
            contentDescription = "Sync Error",
            // 3.2.65 — was Color.Red; theme-aware so the warning reads
            // correctly in both light and dark mode.
            tint               = MaterialTheme.colorScheme.error,
            modifier           = modifier.size(22.dp)
        )
    }
}
