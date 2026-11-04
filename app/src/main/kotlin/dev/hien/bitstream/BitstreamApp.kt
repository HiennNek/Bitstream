package dev.hien.bitstream

import android.app.Application
import dev.hien.bitstream.core.playback.engine.PlaybackEngine

class BitstreamApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    val playbackEngine: PlaybackEngine
        get() = container.playbackEngine
}
