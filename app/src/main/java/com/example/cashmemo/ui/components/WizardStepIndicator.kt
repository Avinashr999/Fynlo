package com.example.cashmemo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WizardStepIndicator(
    steps: List<String>,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier            = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment   = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isActive   = index == currentStep
            val isComplete = index < currentStep

            val dotColor by animateColorAsState(
                targetValue = when {
                    isComplete -> MaterialTheme.colorScheme.primary
                    isActive   -> MaterialTheme.colorScheme.primary
                    else       -> MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = tween(300),
                label = "dot_$index"
            )
            val textColor by animateColorAsState(
                targetValue = if (isActive || isComplete)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(300),
                label = "text_$index"
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier          = Modifier
                        .size(if (isActive) 32.dp else 26.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary
                            else dotColor.copy(alpha = if (isComplete) 0.25f else 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = if (isComplete) "✓" else "${index + 1}",
                        color      = if (isActive) MaterialTheme.colorScheme.onPrimary
                                     else textColor,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = label,
                    fontSize  = 10.sp,
                    color     = textColor,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // Connector line between steps
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                        .background(
                            if (isComplete) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                )
            }
        }
    }
}

