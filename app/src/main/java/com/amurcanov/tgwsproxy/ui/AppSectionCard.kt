package com.amurcanov.tgwsproxy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

@Composable
private fun appSectionCardColor(): Color {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    return if (isDark) {
        lerp(colors.surface, colors.primaryContainer, 0.20f)
    } else {
        lerp(colors.surface, colors.surfaceVariant, 0.28f)
    }
}

@Composable
private fun appSectionCardBorderColor(): Color {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.22f
    return if (isDark) {
        colors.outlineVariant.copy(alpha = 0.52f)
    } else {
        colors.outlineVariant.copy(alpha = 0.24f)
    }
}

@Composable
fun AppSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = appSectionCardColor(),
        border = BorderStroke(1.dp, appSectionCardBorderColor()),
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }
}
