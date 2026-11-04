package dev.hien.bitstream.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import dev.hien.bitstream.core.data.db.ScanFolderDao
import dev.hien.bitstream.core.data.db.ScanFolderEntity
import dev.hien.bitstream.core.data.library.ScanWorker
import dev.hien.bitstream.core.playback.engine.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

data class ScanFolderItem(
    val treeUri: String,
    val displayName: String,
    val lastScan: Long = 0L,
)

data class SettingsUiState(
    val folders: List<ScanFolderItem> = emptyList(),
    val softwareVolume: Boolean = false,
    val gapless: Boolean = true,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = true,
)

interface SettingsStore {
    val folders: StateFlow<List<ScanFolderItem>>
    val softwareVolumeFlow: StateFlow<Boolean>
    val gaplessFlow: StateFlow<Boolean>
    val themeFlow: StateFlow<ThemePreference>
    val dynamicColorFlow: StateFlow<Boolean>

    suspend fun addFolder(uri: String, displayName: String = defaultDisplayName(uri))

    suspend fun removeFolder(uri: String)

    fun rescan()

    fun setSoftwareVolume(on: Boolean)

    fun setGapless(on: Boolean)

    fun setTheme(theme: ThemePreference)

    fun setDynamicColor(enabled: Boolean)
}

class AndroidSettingsStore(
    private val context: Context,
    private val scanFolderDao: ScanFolderDao,
    private val playbackController: PlaybackController,
    private val scope: CoroutineScope,
) : SettingsStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _folders = MutableStateFlow<List<ScanFolderItem>>(emptyList())
    override val folders: StateFlow<List<ScanFolderItem>> = _folders.asStateFlow()

    private val _softwareVolumeFlow = MutableStateFlow(
        prefs.getBoolean(KEY_SOFTWARE_VOLUME, false),
    )
    override val softwareVolumeFlow: StateFlow<Boolean> = _softwareVolumeFlow.asStateFlow()

    private val _gaplessFlow = MutableStateFlow(
        prefs.getBoolean(KEY_GAPLESS, true),
    )
    override val gaplessFlow: StateFlow<Boolean> = _gaplessFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(
        runCatching {
            ThemePreference.valueOf(
                prefs.getString(KEY_THEME, ThemePreference.SYSTEM.name)
                    ?: ThemePreference.SYSTEM.name,
            )
        }.getOrDefault(ThemePreference.SYSTEM),
    )
    override val themeFlow: StateFlow<ThemePreference> = _themeFlow.asStateFlow()

    private val _dynamicColorFlow = MutableStateFlow(
        prefs.getBoolean(KEY_DYNAMIC_COLOR, true),
    )
    override val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    init {
        if (_softwareVolumeFlow.value) {
            playbackController.setSoftwareVolume(true)
        }
        scope.launch {
            scanFolderDao.observeFolders().collect { entities ->
                _folders.value = entities.map { entity ->
                    ScanFolderItem(
                        treeUri = entity.treeUri,
                        displayName = entity.displayName,
                        lastScan = entity.lastScan,
                    )
                }
            }
        }
    }

    override suspend fun addFolder(uri: String, displayName: String) {
        scanFolderDao.upsert(
            ScanFolderEntity(
                treeUri = uri,
                displayName = displayName,
                lastScan = 0L,
            ),
        )
        ScanWorker.enqueue(context, uri)
    }

    override suspend fun removeFolder(uri: String) {
        scanFolderDao.delete(uri)
        ScanWorker.enqueue(context)
    }

    override fun rescan() {
        ScanWorker.enqueue(context)
    }

    override fun setSoftwareVolume(on: Boolean) {
        prefs.edit().putBoolean(KEY_SOFTWARE_VOLUME, on).apply()
        _softwareVolumeFlow.value = on
        playbackController.setSoftwareVolume(on)
    }

    override fun setGapless(on: Boolean) {
        prefs.edit().putBoolean(KEY_GAPLESS, on).apply()
        _gaplessFlow.value = on
    }

    override fun setTheme(theme: ThemePreference) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _themeFlow.value = theme
    }

    override fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColorFlow.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "bitstream_settings"
        const val KEY_SOFTWARE_VOLUME = "software_volume"
        const val KEY_GAPLESS = "gapless"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {
    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val _state = MutableStateFlow(
        SettingsUiState(
            folders = store.folders.value,
            softwareVolume = store.softwareVolumeFlow.value,
            gapless = store.gaplessFlow.value,
            theme = store.themeFlow.value,
            dynamicColor = store.dynamicColorFlow.value,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        vmScope.launch {
            combine(
                store.folders,
                store.softwareVolumeFlow,
                store.gaplessFlow,
                store.themeFlow,
                store.dynamicColorFlow,
            ) { folders, softwareVolume, gapless, theme, dynamicColor ->
                SettingsUiState(
                    folders = folders,
                    softwareVolume = softwareVolume,
                    gapless = gapless,
                    theme = theme,
                    dynamicColor = dynamicColor,
                )
            }.collect { next ->
                _state.value = next
            }
        }
    }

    fun addFolder(uri: String, displayName: String = defaultDisplayName(uri)) {
        vmScope.launch {
            store.addFolder(uri, displayName)
        }
    }

    fun removeFolder(uri: String) {
        vmScope.launch {
            store.removeFolder(uri)
        }
    }

    fun rescan() {
        store.rescan()
    }

    fun setSoftwareVolume(on: Boolean) {
        store.setSoftwareVolume(on)
    }

    fun setGapless(on: Boolean) {
        store.setGapless(on)
    }

    fun setTheme(theme: ThemePreference) {
        store.setTheme(theme)
    }

    fun setDynamicColor(enabled: Boolean) {
        store.setDynamicColor(enabled)
    }

    override fun onCleared() {
        vmScope.cancel()
        super.onCleared()
    }
}

private fun defaultDisplayName(uri: String): String =
    runCatching { Uri.parse(uri).lastPathSegment }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: uri.substringAfterLast('/').ifBlank { "Music" }
