package dev.hien.bitstream.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {
    @Test
    fun `software volume defaults off and persists`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(store)
        assertFalse(vm.state.first().softwareVolume)
        vm.setSoftwareVolume(true)
        assertTrue(store.softwareVolume)
        assertTrue(vm.state.first().softwareVolume)
    }

    @Test
    fun `addFolder removeFolder and rescan update folders and trigger scan`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(store)
        vm.addFolder("content://tree/music", "Music")
        assertEquals(listOf("content://tree/music"), vm.state.first().folders.map { it.treeUri })
        vm.rescan()
        assertEquals(1, store.rescanCount)
        vm.removeFolder("content://tree/music")
        assertTrue(vm.state.first().folders.isEmpty())
    }

    @Test
    fun `setTheme and setDynamicColor update preferences`() = runTest {
        val store = FakeSettingsStore()
        val vm = SettingsViewModel(store)
        vm.setTheme(ThemePreference.DARK)
        vm.setDynamicColor(false)
        assertEquals(ThemePreference.DARK, vm.state.first().theme)
        assertFalse(vm.state.first().dynamicColor)
    }
}

private class FakeSettingsStore : SettingsStore {
    var softwareVolume: Boolean = false
        private set
    var rescanCount: Int = 0
        private set

    private val _folders = MutableStateFlow<List<ScanFolderItem>>(emptyList())
    override val folders: StateFlow<List<ScanFolderItem>> = _folders.asStateFlow()

    private val _softwareVolumeFlow = MutableStateFlow(false)
    override val softwareVolumeFlow: StateFlow<Boolean> = _softwareVolumeFlow.asStateFlow()

    private val _gaplessFlow = MutableStateFlow(true)
    override val gaplessFlow: StateFlow<Boolean> = _gaplessFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(ThemePreference.SYSTEM)
    override val themeFlow: StateFlow<ThemePreference> = _themeFlow.asStateFlow()

    private val _dynamicColorFlow = MutableStateFlow(true)
    override val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

    override suspend fun addFolder(uri: String, displayName: String) {
        _folders.value = _folders.value + ScanFolderItem(uri, displayName, 0L)
    }

    override suspend fun removeFolder(uri: String) {
        _folders.value = _folders.value.filterNot { it.treeUri == uri }
    }

    override fun rescan() {
        rescanCount += 1
    }

    override fun setSoftwareVolume(on: Boolean) {
        softwareVolume = on
        _softwareVolumeFlow.value = on
    }

    override fun setGapless(on: Boolean) {
        _gaplessFlow.value = on
    }

    override fun setTheme(theme: ThemePreference) {
        _themeFlow.value = theme
    }

    override fun setDynamicColor(enabled: Boolean) {
        _dynamicColorFlow.value = enabled
    }
}
