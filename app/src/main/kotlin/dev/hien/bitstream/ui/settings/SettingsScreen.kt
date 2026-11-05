package dev.hien.bitstream.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.playback.usb.DacState

@Composable
fun SettingsRoute(onUsbDiagnostics: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(container.settingsStore) }
        },
    )
    val state by viewModel.state.collectAsState()
    val dacState by container.usbDeviceManager.state.collectAsState()
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addFolder(
                uri = uri.toString(),
                displayName = uri.lastPathSegment ?: "Music",
            )
        }
    }

    SettingsScreen(
        state = state,
        dacState = dacState,
        onAddFolder = { folderPicker.launch(null) },
        onRemoveFolder = viewModel::removeFolder,
        onRescan = viewModel::rescan,
        onSoftwareVolumeChange = viewModel::setSoftwareVolume,
        onGaplessChange = viewModel::setGapless,
        onThemeChange = viewModel::setTheme,
        onDynamicColorChange = viewModel::setDynamicColor,
        onUsbDiagnostics = onUsbDiagnostics,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    dacState: DacState,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onSoftwareVolumeChange: (Boolean) -> Unit,
    onGaplessChange: (Boolean) -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onUsbDiagnostics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
    ) {
        BitstreamLargeHeader(title = "Settings")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .testTag("settings_column"),
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
            ) {
                item {
                    SectionCard(title = "Library") {
                        if (state.folders.isEmpty()) {
                            Text(
                                text = "No music folders added yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.folders.forEach { folder ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = folder.treeUri,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    TextButton(onClick = { onRemoveFolder(folder.treeUri) }) {
                                        Text("Remove")
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = onAddFolder) {
                                Text("Add folder")
                            }
                            OutlinedButton(onClick = onRescan) {
                                Text("Rescan library")
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Playback & Bit-Perfect Output") {
                        val dacStatus = when (dacState) {
                            is DacState.Connected ->
                                "${dacState.handle.name} (UAC ${dacState.device.uacVersion}.0 connected)"
                            is DacState.PermissionNeeded ->
                                "${dacState.name} (USB permission required)"
                            is DacState.Connecting -> "Opening USB DAC…"
                            is DacState.Error -> "USB error: ${dacState.reason}"
                            DacState.NoDevice -> "No USB DAC attached"
                        }
                        Text(
                            text = "DAC status: $dacStatus",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "FiiO / JadeAudio JA11 note: disable PEQ in FiiO Control and keep UAC2 enabled for bit-perfect output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Software volume (not bit-perfect)",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "Applies digital attenuation before USB output and demotes the Bit-perfect badge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.softwareVolume,
                                onCheckedChange = onSoftwareVolumeChange,
                                modifier = Modifier.testTag("settings_software_volume_switch"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gapless playback",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "Pre-decodes the next track into the ring buffer when sample rate and bit depth match.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.gapless,
                                onCheckedChange = onGaplessChange,
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = "Appearance") {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreference.entries.forEach { pref ->
                                FilterChip(
                                    selected = state.theme == pref,
                                    onClick = { onThemeChange(pref) },
                                    label = {
                                        Text(
                                            when (pref) {
                                                ThemePreference.SYSTEM -> "System"
                                                ThemePreference.LIGHT -> "Light"
                                                ThemePreference.DARK -> "Dark"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Album art colors",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = "Tints the app from the current album; off uses deep teal.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.dynamicColor,
                                onCheckedChange = onDynamicColorChange,
                            )
                        }
                    }
                }

                item {
                    SectionCard(title = "Diagnostics") {
                        ListItem(
                            supportingContent = {
                                Text("Inspect UAC descriptors, alt setting, endpoint, sync type, URB stats, and copy raw hex dump")
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings),
                                    contentDescription = null,
                                )
                            },
                            modifier = Modifier
                                .clickable(onClick = onUsbDiagnostics)
                                .testTag("settings_usb_diagnostics_item"),
                            headlineContent = {
                                Text("USB diagnostics")
                            },
                        )
                    }
                }

                item {
                    SectionCard(title = "About") {
                        Text(
                            text = "Bitstream 0.1.0",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Local bit-perfect FLAC player for Android with direct libFLAC + libusb UAC2 isochronous output.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.hero),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
