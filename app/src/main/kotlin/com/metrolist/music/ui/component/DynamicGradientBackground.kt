/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun DynamicGradientBackground(
    isEnabled: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!isEnabled || accentColor == Color.Transparent) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            content()
        }
    } else {
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                Color.Black,
                accentColor.copy(alpha = 0.15f),
                Color.Black
            ),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
        )
        
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            content()
        }
    }
}

@Composable
fun StaticGradientBackground(
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (accentColor == Color.Transparent) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            content()
        }
    } else {
        val gradientBrush = Brush.linearGradient(
            colors = listOf(
                Color.Black,
                accentColor.copy(alpha = 0.15f),
                Color.Black
            ),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
        )
        
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            content()
        }
    }
}
