package com.wnoicew.expensetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple HIG-inspired Translucent Glass Card.
 * Rounded 20dp corners, layered depth, 1px subtle glow border, optional ambient backlight.
 */
@Composable
fun HigGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    elevation: Dp = 4.dp,
    backlightColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        if (backlightColor != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(y = 2.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                backlightColor.copy(alpha = 0.30f),
                                backlightColor.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        shape = shape
                    )
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation, shape, clip = false)
                .border(BorderStroke(1.dp, borderColor), shape),
            shape = shape,
            color = backgroundColor,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Inset Grouped card container (Apple iOS style).
 * Wraps list rows with rounded corners and clean dividers.
 */
@Composable
fun HigInsetGroup(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor), shape),
        shape = shape,
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}
