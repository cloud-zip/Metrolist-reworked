/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object ColorExtractor {

    suspend fun extractAccentColor(bitmap: Bitmap?): Color = withContext(Dispatchers.Default) {
        return@withContext try {
            if (bitmap == null) {
                Color.Transparent
            } else {
                val palette = Palette.from(bitmap).generate()
                
                val vibrantColor = palette.getVibrantColor(0)
                val mutedColor = palette.getMutedColor(0)
                val lightVibrantColor = palette.getLightVibrantColor(0)
                
                val selectedColor = when {
                    vibrantColor != 0 -> vibrantColor
                    lightVibrantColor != 0 -> lightVibrantColor
                    mutedColor != 0 -> mutedColor
                    else -> 0
                }
                
                if (selectedColor != 0) {
                    Color(selectedColor)
                } else {
                    Color.Transparent
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract accent color from bitmap")
            Color.Transparent
        }
    }

    fun extractAccentColorSync(bitmap: Bitmap?): Color {
        return try {
            if (bitmap == null) {
                Color.Transparent
            } else {
                val palette = Palette.from(bitmap).generate()
                
                val vibrantColor = palette.getVibrantColor(0)
                val mutedColor = palette.getMutedColor(0)
                val lightVibrantColor = palette.getLightVibrantColor(0)
                
                val selectedColor = when {
                    vibrantColor != 0 -> vibrantColor
                    lightVibrantColor != 0 -> lightVibrantColor
                    mutedColor != 0 -> mutedColor
                    else -> 0
                }
                
                if (selectedColor != 0) {
                    Color(selectedColor)
                } else {
                    Color.Transparent
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to extract accent color from bitmap")
            Color.Transparent
        }
    }

    fun getBrightnessAdjustedColor(color: Color, brightness: Float = 0.8f): Color {
        if (color == Color.Transparent) return Color.Transparent
        
        val argb = color.value.toLong()
        val a = ((argb shr 24) and 0xFF).toInt()
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        
        val adjustedR = (r * brightness).toInt().coerceIn(0, 255)
        val adjustedG = (g * brightness).toInt().coerceIn(0, 255)
        val adjustedB = (b * brightness).toInt().coerceIn(0, 255)
        
        return Color(
            red = adjustedR / 255f,
            green = adjustedG / 255f,
            blue = adjustedB / 255f,
            alpha = a / 255f
        )
    }
}
