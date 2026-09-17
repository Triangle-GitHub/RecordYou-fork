package com.bnyro.recorder

import android.app.Application
import android.util.Log
import com.bnyro.recorder.util.FileRepository
import com.bnyro.recorder.util.FileRepositoryImpl
import com.bnyro.recorder.util.NotificationHelper
import com.bnyro.recorder.util.Preferences
import com.bnyro.recorder.util.ShortcutHelper
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


class App : Application() {
    val fileRepository: FileRepository by lazy {
        FileRepositoryImpl(this)
    }
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            // Uncaught background errors (e.g. a failing save) must not crash the app.
            Log.e("App", "Unhandled app-scope exception", e)
        }
    )

    override fun onCreate() {
        super.onCreate()
        Preferences.init(this)
        NotificationHelper.buildNotificationChannels(this)
        ShortcutHelper.createShortcuts(this)
    }
}
