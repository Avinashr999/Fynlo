package com.example.cashmemo.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashmemo.data.model.Project

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
            FilterChip(
                selected = selected,
                onClick  = { onSwitch(project.id) },
                label = {
                    Text(
                        text       = project.name,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
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
