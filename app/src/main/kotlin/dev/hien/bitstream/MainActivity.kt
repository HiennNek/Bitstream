package dev.hien.bitstream

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.ui.BitstreamApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BitstreamTheme {
                BitstreamApp()
            }
        }
    }
}
