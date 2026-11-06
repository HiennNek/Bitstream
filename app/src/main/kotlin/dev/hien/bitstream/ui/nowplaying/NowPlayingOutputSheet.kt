package dev.hien.bitstream.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.core.designsystem.Radii
import dev.hien.bitstream.core.designsystem.integrityLabel
import dev.hien.bitstream.core.designsystem.pathLabel
import dev.hien.bitstream.core.model.BitPerfectPath
import dev.hien.bitstream.core.model.VolumeState
import dev.hien.bitstream.core.playback.usb.DacState

/** Output, signal path, and volume controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputSheet(
    state: NowPlayingUiState,
    dacState: DacState,
    onSoftwareVolume: (Boolean) -> Unit,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolumeLevel: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("output_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Audio Output & Signal Path",
                style = MaterialTheme.typography.titleLarge,
            )
            val connected = dacState as? DacState.Connected
            Text(
                text = connected?.handle?.name ?: state.dacName ?: "No USB DAC connected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (connected != null) {
                val device = connected.device
                val formatSummary = device.formats.firstOrNull()?.let { fmt ->
                    "${fmt.channels} ch · ${fmt.subslotBytes * 8}-bit subslot (${fmt.bitResolution} valid bits)"
                } ?: "Standard PCM"
                Text(
                    text = "UAC ${device.uacVersion}.0 · ${device.syncType} · Alt ${device.altSetting} · $formatSummary",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.format != null) {
                Text(
                    text = "Negotiated stream: ${state.format.sampleRate} Hz / ${state.format.bitDepth}-bit (${state.formatLabel})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Path: ${pathLabel(state.path, state.volume)} · Integrity: ${integrityLabel(state.integrity, state.volume)} · ${state.underruns} underruns",
                style = MaterialTheme.typography.bodyMedium,
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
                    checked = state.volume is VolumeState.Software ||
                        state.path == BitPerfectPath.SOFTWARE_VOLUME,
                    onCheckedChange = onSoftwareVolume,
                    modifier = Modifier.testTag("software_volume_switch"),
                )
            }
            VolumeSection(
                volume = state.volume,
                onHardwareVolume = onHardwareVolume,
                onSoftwareVolume = onSoftwareVolume,
                onSoftwareVolumeLevel = onSoftwareVolumeLevel,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Hardware or software volume, mirroring the engine's current mode. */
@Composable
private fun VolumeSection(
    volume: VolumeState?,
    onHardwareVolume: (Int) -> Unit,
    onSoftwareVolume: (Boolean) -> Unit,
    onSoftwareVolumeLevel: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(Radii.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (volume) {
                is VolumeState.Hardware -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Hardware volume (UAC)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = "${volume.level}%",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("hardware_volume_value"),
                        )
                    }
                    Slider(
                        value = volume.level / 100f,
                        onValueChange = { fraction ->
                            onHardwareVolume((fraction * 100f).toInt().coerceIn(0, 100))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hardware_volume_slider"),
                    )
                }

                is VolumeState.Software -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Software volume (${volume.level}%) — not bit-perfect",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Text(
                                text = "Q1.30 digital attenuation + TPDF dither active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = { onSoftwareVolume(false) },
                            modifier = Modifier.testTag("restore_bit_perfect_button"),
                        ) {
                            Text("Restore")
                        }
                    }
                    Slider(
                        value = volume.level / 100f,
                        onValueChange = { fraction ->
                            onSoftwareVolumeLevel((fraction * 100f).toInt().coerceIn(0, 100))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("software_volume_slider"),
                    )
                }

                VolumeState.Fixed, null -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Fixed output",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.testTag("fixed_output_label"),
                        )
                        Text(
                            text = "Unity / DAC hardware",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = 1f,
                        onValueChange = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
