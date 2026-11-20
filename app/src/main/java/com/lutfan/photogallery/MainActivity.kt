package com.lutfan.photogallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.lutfan.photogallery.ui.GalleryRoot
import com.lutfan.photogallery.ui.theme.GalleryTheme
import com.lutfan.photogallery.ui.theme.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Palette.Paper.toArgb(), Palette.Charcoal.toArgb()),
        )
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        setContent {
            GalleryTheme { GalleryRoot() }
        }
    }

    /**
     * Some skins (e.g. HyperOS) keep apps at 60 Hz unless they ask. Pick the
     * fastest mode at the current resolution so scrolling/editing runs at 120 Hz.
     */
    private fun requestHighestRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }
}
