package com.automattic.wordpresstv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** WordPress brand blue, sampled from the shared app-icon artwork (`#003DE0`). */
val BrandBlue = Color(0xFF003DE0)

/**
 * Dark, TV-first theme. The app is always dark (a 10-foot couch experience), so
 * there's a single color scheme. All components come from `androidx.tv.material3`.
 */
@Composable
fun WordPressTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BrandBlue,
            background = Color.Black,
            surface = Color.Black,
        ),
        content = content,
    )
}
