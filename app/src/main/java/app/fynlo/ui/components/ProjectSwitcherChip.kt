package app.fynlo.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fynlo.data.model.Project
import app.fynlo.ui.theme.Emerald100
import app.fynlo.ui.theme.Emerald700
import app.fynlo.ui.theme.TemplateBorder

@Composable
fun ProjectSwitcherChip(
    projects: List<Project>,
    currentProjectId: String,
    onSwitch: (String) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        projects.forEach { project ->
            val selected = project.id == currentProjectId
            Surface(
                onClick = { onSwitch(project.id) },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) Emerald100.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.8.dp, TemplateBorder),
                shadowElevation = if (selected) 2.dp else 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (selected) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = Emerald700)
                    }
                    Text(
                        text       = project.name,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Emerald700 else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // "+ New" button
        AssistChip(
            onClick = onManageClick,
            label   = { Text("Manage") },
            leadingIcon = {
                Icon(Icons.Default.Add, contentDescription = "Manage projects", modifier = Modifier.size(16.dp))
            }
        )
    }
}

