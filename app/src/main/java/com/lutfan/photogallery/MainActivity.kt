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
        setContent {
            GalleryTheme { GalleryRoot() }
        }
    }
}
