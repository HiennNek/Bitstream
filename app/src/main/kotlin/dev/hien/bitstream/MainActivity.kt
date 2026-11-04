package dev.hien.bitstream

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import dev.hien.bitstream.core.designsystem.BitstreamTheme
import dev.hien.bitstream.ui.BitstreamApp as BitstreamUi
import dev.hien.bitstream.ui.settings.ThemePreference

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* User choice is respected; playback still works if denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationsIfNeeded()
        val container = (application as BitstreamApp).container
        setContent {
            val themePref by container.settingsStore.themeFlow.collectAsState()
            val dynamicColor by container.settingsStore.dynamicColorFlow.collectAsState()
            val darkTheme = when (themePref) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                BitstreamTheme(
                    darkTheme = darkTheme,
                    dynamicColor = dynamicColor,
                ) {
                    BitstreamUi()
                }
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
