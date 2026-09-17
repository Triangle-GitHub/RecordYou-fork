package com.bnyro.recorder

import android.app.Application
import com.bnyro.recorder.util.FileRepository
import com.bnyro.recorder.util.FileRepositoryImpl
import com.bnyro.recorder.util.NotificationHelper
import com.bnyro.recorder.util.Preferences
import com.bnyro.recorder.util.ShortcutHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val fileRepository: FileRepository by lazy {
        FileRepositoryImpl(this)
    }

    /**
     * Application-scoped coroutine context that survives service destruction, used to
     * finish long-running save operations (e.g. moving a WAV into the output directory)
     * even when the recording service is already being torn down.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        NotificationHelper.buildNotificationChannels(this)
        ShortcutHelper.createShortcuts(this)
    }
}
