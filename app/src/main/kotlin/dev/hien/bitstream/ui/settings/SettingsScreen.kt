package dev.hien.bitstream.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.LocalBottomOverlayPadding
import dev.hien.bitstream.core.designsystem.SegmentedColumn
import dev.hien.bitstream.core.designsystem.SegmentedItemSurface
import dev.hien.bitstream.core.designsystem.SegmentedSwitchRow
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val bottomOverlay = LocalBottomOverlayPadding.current
    val haptic = LocalHapticFeedback.current
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
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.screen + bottomOverlay,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
            ) {
                item {
                    SegmentedColumn(
                        title = "Library",
                        content = buildList {
                            if (state.folders.isEmpty()) {
                                add {
                                    SegmentedItemSurface {
                                        Text(
                                            text = "No music folders added yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(Spacing.lg),
                                        )
                                    }
                                }
                            } else {
                                state.folders.forEach { folder ->
                                    add {
                                        SegmentedItemSurface {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
                                }
                            }
                            add {
                                SegmentedItemSurface {
                                    Row(
                                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        FilledTonalButton(onClick = onAddFolder) {
                                            Text("Add folder")
                                        }
                                        OutlinedButton(onClick = onRescan) {
                                            Text("Rescan library")
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                item {
                    val dacStatus = when (dacState) {
                        is DacState.Connected ->
                            "${dacState.handle.name} (UAC ${dacState.device.uacVersion}.0 connected)"
                        is DacState.PermissionNeeded ->
                            "${dacState.name} (USB permission required)"
                        is DacState.Connecting -> "Opening USB DAC…"
                        is DacState.Error -> "USB error: ${dacState.reason}"
                        DacState.NoDevice -> "No USB DAC attached"
                    }
                    SegmentedColumn(
                        title = "Playback & Bit-Perfect Output",
                        content = listOf(
                            {
                                SegmentedItemSurface {
                                    Column(
                                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Text(
                                            text = "DAC status: $dacStatus",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            text = "FiiO / JadeAudio JA11 note: disable PEQ in FiiO Control and keep UAC2 enabled for bit-perfect output.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            {
                                SegmentedSwitchRow(
                                    title = "Software volume (not bit-perfect)",
                                    summary = "Applies digital attenuation before USB output and demotes the Bit-perfect badge.",
                                    checked = state.softwareVolume,
                                    onCheckedChange = onSoftwareVolumeChange,
                                    switchModifier = Modifier.testTag("settings_software_volume_switch"),
                                )
                            },
                            {
                                SegmentedSwitchRow(
                                    title = "Gapless playback",
                                    summary = "Pre-decodes the next track into the ring buffer when sample rate and bit depth match.",
                                    checked = state.gapless,
                                    onCheckedChange = onGaplessChange,
                                )
                            },
                        ),
                    )
                }

                item {
                    SegmentedColumn(
                        title = "Appearance",
                        content = listOf(
                            {
                                SegmentedItemSurface {
                                    Column(
                                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                    ) {
                                        Text(
                                            text = "Theme",
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        val entries = ThemePreference.entries
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                        ) {
                                            entries.forEachIndexed { index, pref ->
                                                ToggleButton(
                                                    checked = state.theme == pref,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                                            onThemeChange(pref)
                                                        }
                                                    },
                                                    colors = ToggleButtonDefaults.colors(
                                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                                    ),
                                                    shapes = when (index) {
                                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                        entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .semantics { role = Role.RadioButton },
                                                ) {
                                                    Text(
                                                        when (pref) {
                                                            ThemePreference.SYSTEM -> "System"
                                                            ThemePreference.LIGHT -> "Light"
                                                            ThemePreference.DARK -> "Dark"
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            {
                                SegmentedSwitchRow(
                                    title = "Album art colors",
                                    summary = "Tints the app from the current album; off uses wallpaper colors.",
                                    checked = state.dynamicColor,
                                    onCheckedChange = onDynamicColorChange,
                                )
                            },
                        ),
                    )
                }

                item {
                    SegmentedColumn(
                        title = "Diagnostics",
                        content = listOf {
                            SegmentedItemSurface(
                                onClick = onUsbDiagnostics,
                                modifier = Modifier.testTag("settings_usb_diagnostics_item"),
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    supportingContent = {
                                        Text("Inspect UAC descriptors, alt setting, endpoint, sync type, URB stats, and copy raw hex dump")
                                    },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_settings),
                                            contentDescription = null,
                                        )
                                    },
                                    content = {
                                        Text("USB diagnostics")
                                    },
                                )
                            }
                        },
                    )
                }

                item {
                    SegmentedColumn(
                        title = "About",
                        content = listOf {
                            SegmentedItemSurface {
                                Column(
                                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                                ) {
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
                        },
                    )
                }
            }
        }
    }
}
