package dev.hien.bitstream.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hien.bitstream.LocalAppContainer
import dev.hien.bitstream.R
import dev.hien.bitstream.core.playback.native.NativeAudio
import dev.hien.bitstream.core.playback.usb.DacState
import dev.hien.bitstream.core.playback.usb.SyncType
import dev.hien.bitstream.core.playback.usb.UacDevice
import dev.hien.bitstream.core.playback.usb.UacFormat
import dev.hien.bitstream.core.playback.usb.UsbDeviceHandle
import dev.hien.bitstream.core.playback.usb.VolumeRange
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun UsbDebugRoute(onBack: () -> Unit) {
    val manager = LocalAppContainer.current.usbDeviceManager
    val state by manager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        manager.refresh()
    }

    UsbDebugScreen(
        state = state,
        devices = manager.devices(),
        onConnect = manager::connect,
        onConnectByKey = manager::connectByKey,
        onRefresh = manager::refresh,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDebugScreen(
    state: DacState,
    devices: List<UsbDeviceHandle>,
    onConnect: (UsbDeviceHandle) -> Unit,
    onConnectByKey: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("USB diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text("Rescan")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    StatusCard(
                        state = state,
                        onConnectByKey = onConnectByKey,
                        onRefresh = onRefresh,
                    )
                }

                item {
                    Text(
                        text = "Detected audio devices",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                if (devices.isEmpty()) {
                    item {
                        Text(
                            text = "No USB audio device is attached. Connect a USB DAC and rescan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(devices, key = { it.key }) { handle ->
                        DeviceRow(
                            handle = handle,
                            connected = (state as? DacState.Connected)
                                ?.handle
                                ?.key == handle.key,
                            onConnect = onConnect,
                        )
                    }
                }

                val connected = state as? DacState.Connected
                if (connected != null) {
                    item {
                        Text(
                            text = "Parsed device",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item { DeviceDetails(connected.device) }
                    item { FormatsSection(connected.device.formats) }
                    item { NativePlaybackCard(connected) }
                    item { DescriptorSection(connected.device.rawDescriptor) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: DacState,
    onConnectByKey: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                DacState.NoDevice -> {
                    Text("No DAC connected", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Bitstream only outputs to a USB DAC. Attach one to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is DacState.PermissionNeeded -> {
                    Text("Permission needed", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Android must grant Bitstream access to ${state.name} before it can be opened.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(onClick = { onConnectByKey(state.key) }) {
                        Text("Grant permission")
                    }
                }

                is DacState.Connecting -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Opening the DAC…", style = MaterialTheme.typography.titleMedium)
                    }
                }

                is DacState.Connected -> {
                    Text(
                        "Connected to ${state.handle.name}",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        summary(state.device),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("fd ${state.fileDescriptor}") })
                        AssistChip(
                            onClick = {},
                            label = { Text(if (state.device.uacVersion == 2) "UAC2" else "UAC1") },
                        )
                    }
                }

                is DacState.Error -> {
                    Text("USB error", style = MaterialTheme.typography.titleLarge)
                    Text(state.reason, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(onClick = onRefresh) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    handle: UsbDeviceHandle,
    connected: Boolean,
    onConnect: (UsbDeviceHandle) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            supportingContent = {
                Text(
                    "%04x:%04x • %s".format(
                        Locale.US,
                        handle.vendorId,
                        handle.productId,
                        if (handle.hasPermission) "permission granted" else "permission required",
                    ),
                )
            },
            trailingContent = {
                if (connected) {
                    AssistChip(onClick = {}, label = { Text("Connected") })
                } else {
                    FilledTonalButton(onClick = { onConnect(handle) }) {
                        Text(if (handle.hasPermission) "Connect" else "Grant")
                    }
                }
            },
        ) {
            Text(handle.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DeviceDetails(device: UacDevice) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoRow("UAC version", "USB Audio Class ${device.uacVersion}.0")
            InfoRow("Control interface", device.controlInterface.toString())
            InfoRow("Streaming interface", device.streamingInterface.toString())
            InfoRow("Alt setting", device.altSetting.toString())
            InfoRow("Endpoint", "0x%02X (OUT)".format(Locale.US, device.endpointAddress))
            InfoRow("Sync", syncLabel(device.syncType))
            device.feedbackEndpoint?.let {
                InfoRow("Feedback endpoint", "0x%02X".format(Locale.US, it))
            }
            InfoRow("Max packet", "${device.maxPacketSize} bytes")
            InfoRow("Interval", device.interval.toString())
            device.clockSourceId?.let { InfoRow("Clock source", it.toString()) }
            device.featureUnitId?.let { InfoRow("Feature unit", it.toString()) }
            device.volume?.let { InfoRow("Volume range", volumeLabel(it)) }
        }
    }
}

@Composable
private fun FormatsSection(formats: List<UacFormat>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Formats", style = MaterialTheme.typography.titleMedium)
            formats.forEachIndexed { index, format ->
                if (index > 0) HorizontalDivider()
                InfoRow("Channels", format.channels.toString())
                InfoRow("Subslot", "${format.subslotBytes} bytes")
                InfoRow("Bit resolution", "${format.bitResolution}-bit")
                InfoRow(
                    "Sample rates",
                    if (format.sampleRates.isEmpty()) {
                        "Not reported by the descriptor"
                    } else {
                        format.sampleRates.joinToString(" / ", transform = ::formatRate)
                    },
                )
            }
        }
    }
}

@Composable
private fun NativePlaybackCard(connected: DacState.Connected) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val device = connected.device
    val silenceFormat = remember(device) {
        device.formats.firstOrNull { 48_000 in it.sampleRates } ?: device.formats.firstOrNull()
    }
    var handle by remember(connected.handle.key) { mutableStateOf(0L) }
    var status by remember(connected.handle.key) {
        mutableStateOf("Ready — native playback is idle.")
    }
    var stats by remember(connected.handle.key) { mutableStateOf<LongArray?>(null) }

    fun closeHandle() {
        if (handle != 0L) {
            NativeAudio.nativeStop(handle)
            NativeAudio.nativeClose(handle)
            handle = 0L
            stats = null
        }
    }

    suspend fun playMatching(predicate: (dev.hien.bitstream.core.model.Track) -> Boolean) {
        closeHandle()
        val track = runCatching {
            container.musicRepository.tracks.first().firstOrNull(predicate)
        }.getOrNull()
        if (track == null) {
            status = "No playable FLAC track in the library."
            return
        }
        val fileFormat = device.formats.firstOrNull {
            it.channels == track.channels &&
                it.subslotBytes * 8 >= track.bitDepth &&
                (it.sampleRates.isEmpty() || track.sampleRate in it.sampleRates)
        }
        if (fileFormat == null) {
            status = "${track.sampleRate} Hz / ${track.bitDepth}-bit " +
                "is not supported by this DAC."
            return
        }
        val pfd = runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")
        }.getOrNull()
        if (pfd == null) {
            status = "Could not open the track file."
            return
        }
        val opened = NativeAudio.nativeOpen(
            fd = connected.fileDescriptor,
            endpoint = device.endpointAddress,
            interfaceNumber = device.streamingInterface,
            altSetting = device.altSetting,
            subslotBytes = fileFormat.subslotBytes,
            channels = track.channels,
            sampleRate = track.sampleRate,
            syncType = device.syncType.ordinal,
            feedbackEndpoint = device.feedbackEndpoint ?: 0,
            clockSourceId = device.clockSourceId ?: 0,
        )
        if (opened == 0L) {
            pfd.close()
            status = "nativeOpen failed — check logcat for the libusb error."
            return
        }
        handle = opened
        val result = NativeAudio.nativePlayFile(
            handle = opened,
            fd = pfd.fd,
            startFrame = 0,
            sampleRate = track.sampleRate,
            bitDepth = track.bitDepth,
            channels = track.channels,
            subslotBytes = fileFormat.subslotBytes,
        )
        pfd.close()
        status = if (result == 0) {
            "Playing ${track.title} (${track.sampleRate} Hz / ${track.bitDepth}-bit)."
        } else {
            "nativePlayFile failed with code $result."
        }
    }

    DisposableEffect(connected.handle.key) {
        onDispose { closeHandle() }
    }

    LaunchedEffect(handle) {
        while (handle != 0L) {
            stats = NativeAudio.nativeStats(handle)
            delay(200)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Native playback", style = MaterialTheme.typography.titleMedium)
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stats?.let { values ->
                Text(
                    text = "state ${stateLabel(values[0].toInt())} • " +
                        "underruns ${values[1]} • frames ${values[2]} • " +
                        "MD5 ${md5Label(values[3].toInt())} • error ${values[4]}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    enabled = silenceFormat != null,
                    onClick = {
                        closeHandle()
                        if (silenceFormat == null) {
                            status = "No PCM format is available."
                            return@FilledTonalButton
                        }
                        val opened = NativeAudio.nativeOpen(
                            fd = connected.fileDescriptor,
                            endpoint = device.endpointAddress,
                            interfaceNumber = device.streamingInterface,
                            altSetting = device.altSetting,
                            subslotBytes = silenceFormat.subslotBytes,
                            channels = silenceFormat.channels,
                            sampleRate = 48_000,
                            syncType = device.syncType.ordinal,
                            feedbackEndpoint = device.feedbackEndpoint ?: 0,
                            clockSourceId = device.clockSourceId ?: 0,
                        )
                        if (opened == 0L) {
                            status = "nativeOpen failed — check logcat for the libusb error."
                            return@FilledTonalButton
                        }
                        handle = opened
                        val result = NativeAudio.nativeStartSilence(opened, 96_000L)
                        status = if (result == 0) {
                            "Streaming 2 s of silence at 48 kHz."
                        } else {
                            "nativeStartSilence failed with code $result."
                        }
                    },
                ) {
                    Text("2 s silence")
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            playMatching { it.sampleRate > 0 && it.bitDepth > 0 }
                        }
                    },
                ) {
                    Text("Play first library track")
                }
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            playMatching { it.sampleRate == 96_000 && it.bitDepth == 24 }
                        }
                    },
                ) {
                    Text("Play 24/96")
                }
            }
        }
    }
}

private fun stateLabel(state: Int): String = when (state) {
    0 -> "idle"
    1 -> "playing"
    2 -> "ended"
    3 -> "error"
    else -> state.toString()
}

private fun md5Label(state: Int): String = when (state) {
    0 -> "unavailable/partial"
    1 -> "verified"
    2 -> "failed"
    else -> state.toString()
}

@Composable
private fun DescriptorSection(rawDescriptor: ByteArray) {
    val context = LocalContext.current
    val hex = remember(rawDescriptor) { hexDump(rawDescriptor) }
    var copied by remember { mutableStateOf(false) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Raw configuration descriptor", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${rawDescriptor.size} bytes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("USB descriptor", hex))
                        copied = true
                    },
                ) {
                    Text(if (copied) "Copied" else "Copy hex")
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
            ) {
                SelectionContainer {
                    Text(
                        text = hex,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(150.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun summary(device: UacDevice): String {
    val format = device.formats.firstOrNull()
    val formatText = format?.let {
        "${it.channels} ch • ${it.bitResolution}-bit • ${it.subslotBytes}-byte subslot"
    } ?: "No PCM format"
    return "Alt ${device.altSetting} • $formatText • ${syncLabel(device.syncType)}"
}

private fun syncLabel(syncType: SyncType): String = when (syncType) {
    SyncType.ASYNC -> "Asynchronous"
    SyncType.ADAPTIVE -> "Adaptive"
    SyncType.SYNC -> "Synchronous"
    SyncType.NONE -> "None"
}

private fun volumeLabel(volume: VolumeRange): String =
    "${decibels(volume.min)} dB to ${decibels(volume.max)} dB, " +
        "step ${decibels(volume.resolution)} dB"

private fun decibels(value: Int): String =
    String.format(Locale.US, "%.1f", value / 256.0)

private fun formatRate(hz: Int): String =
    if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    }

private fun hexDump(bytes: ByteArray): String = buildString {
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
