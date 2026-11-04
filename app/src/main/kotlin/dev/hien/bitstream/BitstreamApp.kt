package dev.hien.bitstream

import android.app.Application

class BitstreamApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
