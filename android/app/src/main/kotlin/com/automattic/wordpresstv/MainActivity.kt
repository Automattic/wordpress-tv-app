package com.automattic.wordpresstv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.automattic.wordpresstv.ui.theme.WordPressTvTheme

/**
 * The single Activity. It declares `LEANBACK_LAUNCHER` in the manifest, so the
 * Google TV home screen launches straight into this Compose hierarchy.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WordPressTvTheme {
                WordPressTvApp()
            }
        }
    }
}
