package dev.hien.bitstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.ui.BitstreamApp as BitstreamUi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as BitstreamApp).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                BitstreamTheme {
                    BitstreamUi()
                }
            }
        }
    }
}
