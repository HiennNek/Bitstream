package dev.hien.bitstream.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import dev.hien.bitstream.core.designsystem.GoogleSansCode
import androidx.compose.ui.unit.dp
import dev.hien.bitstream.core.designsystem.BitstreamLargeHeader
import dev.hien.bitstream.core.designsystem.LabeledValueRow
import dev.hien.bitstream.core.designsystem.LocalBottomOverlayPadding
import dev.hien.bitstream.core.designsystem.SegmentedColumn
import dev.hien.bitstream.core.designsystem.SegmentedItemSurface
import dev.hien.bitstream.core.designsystem.Spacing
import dev.hien.bitstream.core.designsystem.TopBarBackButton
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.model.PlaybackState
import dev.hien.bitstream.core.playback.engine.PlaybackError
import dev.hien.bitstream.core.playback.usb.DacState
import dev.hien.bitstream.core.playback.usb.SyncType
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DiagnosticsSummary(
    val uacVersion: String,
    val altSetting: String,
    val endpoint: String,
    val syncType: String,
    val subslotAndValidBits: String,
    val sampleRate: String,
    val ringFill: String,
    val urbStats: String,
    val libusbErrors: String,
    val descriptorDump: String,
)

fun buildDiagnosticsSummary(
    dacState: DacState,
    playbackState: PlaybackState,
    ringFillPercent: Int = 0,
    lastNativeError: Int = 0,
): DiagnosticsSummary {
    val errorSummary = when {
        lastNativeError != 0 -> "Error $lastNativeError (${PlaybackError.fromNativeCode(lastNativeError)})"
        dacState is DacState.Error -> dacState.reason
        else -> "0 errors"
    }
    val ringSummary = when {
        ringFillPercent > 0 -> "$ringFillPercent%"
        playbackState.track != null -> "0% (draining)"
        else -> "0% (idle)"
    }
    val connected = dacState as? DacState.Connected
    if (connected == null) {
        return DiagnosticsSummary(
            uacVersion = "N/A",
            altSetting = "N/A",
            endpoint = "N/A",
            syncType = "N/A",
            subslotAndValidBits = "N/A",
            sampleRate = "N/A",
            ringFill = ringSummary,
            urbStats = "${playbackState.underruns} underruns",
            libusbErrors = errorSummary,
            descriptorDump = "",
        )
    }

    val device = connected.device
    val track = playbackState.track
    val activeFormat = device.formats.firstOrNull { fmt ->
        track != null &&
            fmt.channels == track.channels &&
            fmt.subslotBytes * 8 >= track.bitDepth &&
            (fmt.sampleRates.isEmpty() || track.sampleRate in fmt.sampleRates)
    } ?: device.formats.firstOrNull()

    val syncLabel = when (device.syncType) {
        SyncType.ASYNC -> "Asynchronous (0x04)"
        SyncType.ADAPTIVE -> "Adaptive (0x08)"
        SyncType.SYNC -> "Synchronous (0x0C)"
        SyncType.NONE -> "None"
    }
    val subslotText = activeFormat?.let {
        "${it.subslotBytes * 8}-bit subslot / ${it.bitResolution} valid bits"
    } ?: "Unknown"
    val rateText = track?.takeIf { it.sampleRate > 0 }?.let { "${it.sampleRate} Hz" }
        ?: activeFormat?.sampleRates?.firstOrNull()?.let { "$it Hz" }
        ?: "Idle"

    return DiagnosticsSummary(
        uacVersion = "UAC ${device.uacVersion}.0",
        altSetting = "Interface ${device.streamingInterface}, Alt ${device.altSetting}",
        endpoint = String.format(Locale.US, "0x%02X (ISO OUT, max %d B)", device.endpointAddress, device.maxPacketSize),
        syncType = syncLabel,
        subslotAndValidBits = subslotText,
        sampleRate = rateText,
        ringFill = ringSummary,
        urbStats = "4 URBs × 16 pkts • ${playbackState.underruns} underruns",
        libusbErrors = errorSummary,
        descriptorDump = formatDescriptorHex(device.rawDescriptor),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
) {
    val container = LocalAppContainer.current
    val dacState by container.usbDeviceManager.state.collectAsState()
    val playbackState by container.playbackEngine.state.collectAsState()
    val ringFillPercent by container.audioBackend.ringFillPercent.collectAsState()
    val lastNativeError by container.audioBackend.lastNativeError.collectAsState()
    val summary = remember(dacState, playbackState, ringFillPercent, lastNativeError) {
        buildDiagnosticsSummary(
            dacState = dacState,
            playbackState = playbackState,
            ringFillPercent = ringFillPercent,
            lastNativeError = lastNativeError,
        )
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    val bottomOverlay = LocalBottomOverlayPadding.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
    ) {
        BitstreamLargeHeader(
            title = "USB Audio Diagnostics",
            navigationIcon = {
                TopBarBackButton(
                    onClick = onBack,
                    painter = painterResource(R.drawable.ic_arrow_back),
                )
            },
        ) {
            TextButton(onClick = { scope.launch(Dispatchers.Default) { container.usbDeviceManager.refresh() } }) {
                Text("Refresh")
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = Spacing.sm,
                    bottom = Spacing.screen + bottomOverlay,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
            ) {
                if (dacState is DacState.PermissionNeeded) {
                    val needed = dacState as DacState.PermissionNeeded
                    item {
                        SegmentedColumn(
                            content = listOf {
                                SegmentedItemSurface {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.lg),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "USB permission required for ${needed.name}",
                                            modifier = Modifier.weight(1f),
                                        )
                                        Button(onClick = { scope.launch(Dispatchers.Default) { container.usbDeviceManager.connectByKey(needed.key) } }) {
                                            Text("Grant permission")
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                item {
                    val rows = listOf(
                        "UAC version" to summary.uacVersion,
                        "Alt setting" to summary.altSetting,
                        "Endpoint" to summary.endpoint,
                        "Sync type" to summary.syncType,
                        "Subslot / valid bits" to summary.subslotAndValidBits,
                        "Sample rate" to summary.sampleRate,
                        "Ring buffer fill" to summary.ringFill,
                        "URB stats" to summary.urbStats,
                        "libusb errors" to summary.libusbErrors,
                    )
                    SegmentedColumn(
                        title = "Stream & USB Isochronous Telemetry",
                        content = rows.map { (label, value) ->
                            @Composable {
                                SegmentedItemSurface {
                                    LabeledValueRow(
                                        label = label,
                                        value = value,
                                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                                    )
                                }
                            }
                        },
                    )
                }

                item {
                    SegmentedColumn(
                        title = "Raw Descriptor Dump",
                        content = listOf {
                            SegmentedItemSurface {
                                Column(
                                    modifier = Modifier.padding(Spacing.lg),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Hex dump",
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        TextButton(
                                            enabled = summary.descriptorDump.isNotEmpty(),
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                                    as ClipboardManager
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText(
                                                        "USB descriptor",
                                                        summary.descriptorDump,
                                                    ),
                                                )
                                                copied = true
                                            },
                                            modifier = Modifier.testTag("copy_descriptor_button"),
                                        ) {
                                            Text(if (copied) "Copied" else "Copy descriptor")
                                        }
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = summary.descriptorDump.ifEmpty {
                                                    "No USB DAC descriptor available."
                                                },
                                                modifier = Modifier
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(Spacing.md),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = GoogleSansCode,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun formatDescriptorHex(bytes: ByteArray): String = buildString {
    var offset = 0
    while (offset < bytes.size) {
        if (isNotEmpty()) append('\n')
        append(offset.toString(16).padStart(4, '0'))
        append("  ")
        val end = minOf(offset + 16, bytes.size)
        for (index in offset until end) {
            append((bytes[index].toInt() and 0xFF).toString(16).padStart(2, '0'))
            append(' ')
        }
        offset = end
    }
}
