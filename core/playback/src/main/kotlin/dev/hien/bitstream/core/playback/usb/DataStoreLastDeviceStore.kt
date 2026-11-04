package dev.hien.bitstream.core.playback.usb

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.usbDataStore by preferencesDataStore(name = "usb")

/** DataStore-backed remembered device key. */
class DataStoreLastDeviceStore(context: Context) : LastDeviceStore {
    private val appContext = context.applicationContext

    override val lastDeviceKey: Flow<String?> =
        appContext.usbDataStore.data.map { it[LAST_DEVICE_KEY] }

    override suspend fun setLastDeviceKey(key: String?) {
        appContext.usbDataStore.edit { preferences ->
            if (key == null) {
                preferences.remove(LAST_DEVICE_KEY)
            } else {
                preferences[LAST_DEVICE_KEY] = key
            }
        }
    }

    private companion object {
        val LAST_DEVICE_KEY = stringPreferencesKey("last_device_key")
    }
}
