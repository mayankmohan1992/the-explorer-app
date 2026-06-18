package com.explorer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.explorer.app.ui.theme.GlassBorderCyan
import com.explorer.app.ui.theme.GlassBorderPurple
import com.explorer.app.ui.theme.GlassSurface

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    borderColors: List<Color> = listOf(GlassBorderCyan, GlassBorderPurple),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassSurface)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(borderColors),
                shape = shape
            )
    ) {
        content()
    }
}
