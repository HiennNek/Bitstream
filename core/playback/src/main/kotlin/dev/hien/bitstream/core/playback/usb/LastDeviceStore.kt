package dev.hien.bitstream.core.playback.usb

import kotlinx.coroutines.flow.Flow

/** Persists the last device the user chose so it can be preferred later. */
interface LastDeviceStore {
    val lastDeviceKey: Flow<String?>
    suspend fun setLastDeviceKey(key: String?)
}
