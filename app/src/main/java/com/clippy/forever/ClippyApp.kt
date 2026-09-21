package com.clippy.forever

import android.app.Application

class ClippyApp : Application() {
    lateinit var store: ClipStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = ClipStore(this)
        SaveNotifier.ensureChannel(this)
    }

    companion object {
        lateinit var instance: ClippyApp
            private set
    }
}
