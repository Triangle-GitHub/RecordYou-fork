package com.bnyro.recorder.ui.models

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.bnyro.recorder.App
import com.bnyro.recorder.R
import com.bnyro.recorder.canvas_overlay.CanvasOverlay
import com.bnyro.recorder.enums.AudioDeviceSource
import com.bnyro.recorder.enums.AudioSource
import com.bnyro.recorder.enums.RecorderState
import com.bnyro.recorder.services.AudioRecorderService
import com.bnyro.recorder.services.LosslessRecorderService
import com.bnyro.recorder.services.RecorderService
import com.bnyro.recorder.services.ScreenRecorderService
import com.bnyro.recorder.util.PermissionHelper
import com.bnyro.recorder.util.Preferences
import com.bnyro.recorder.util.WavFinalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** Outcome of a background save, shown briefly in the UI banner. */
class SaveOutcome(val ok: Boolean, val name: String?)
class RecorderModel : ViewModel() {
    private val supportsOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    var recorderState by mutableStateOf(RecorderState.IDLE)
    var isSaving by mutableStateOf(false)
    var saveDone: SaveOutcome? by mutableStateOf(null)
    var pendingBannerDismissed by mutableStateOf(false)
    var pendingRecordings by mutableStateOf<List<WavFinalizer.PendingRecording>>(emptyList())
    var recordedTime by mutableStateOf<Long?>(null)
    val recordedAmplitudes = mutableStateListOf<Int>()
    private var activityResult: ActivityResult? = null
    private var canvasOverlay: CanvasOverlay? = null

    private val handler = Handler(Looper.getMainLooper())
    private var saveDoneRunnable: Runnable? = null
    private val saveDoneDismissMs = 3_000L

    @SuppressLint("StaticFieldLeak")
    private var recorderService: RecorderService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            recorderService = (service as RecorderService.LocalBinder).getService()
            recorderService?.onRecorderStateChanged = {
                recorderState = it
            }
            recorderService?.onSaveStateChanged = { saving, name ->
                isSaving = saving
                if (!saving) markSaveDone(name != null, name)
            }
            (recorderService as? ScreenRecorderService)?.prepare(activityResult!!)
            if (supportsOverlay) canvasOverlay?.show()
            recorderService?.start()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            recorderService = null
        }
    }

    fun startVideoRecorder(context: Context, result: ActivityResult) {
        activityResult = result
        val serviceIntent = Intent(context, ScreenRecorderService::class.java)
        startRecorderService(context, serviceIntent)
        val showOverlayAnnotation =
            Preferences.prefs.getBoolean(Preferences.showOverlayAnnotationToolKey, false)
        if (supportsOverlay && showOverlayAnnotation) {
            canvasOverlay = CanvasOverlay(context)
        }
    }

    @SuppressLint("NewApi")
    fun startAudioRecorder(context: Context) {
        val audioPermission = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermission.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val internalAudio = Preferences.prefs.getInt(
            Preferences.audioDeviceSourceKey,
            0
        ) == AudioDeviceSource.REMOTE_SUBMIX.value
        if (internalAudio) {
            audioPermission.add(Manifest.permission.CAPTURE_AUDIO_OUTPUT)
        }

        if (!PermissionHelper.checkPermissions(context, audioPermission.toTypedArray())) {
            Toast.makeText(
                context,
                context.getString(R.string.no_sufficient_permissions), Toast.LENGTH_SHORT
            )
                .show()
            return
        }

        val serviceIntent =
            if (Preferences.prefs.getBoolean(Preferences.losslessRecorderKey, false)) {
                Intent(context, LosslessRecorderService::class.java)
            } else {
                Intent(context, AudioRecorderService::class.java)
            }

        startRecorderService(context, serviceIntent)
    }

    private fun startRecorderService(context: Context, intent: Intent) {
        runCatching {
            context.unbindService(connection)
        }

        listOfNotNull(
            AudioRecorderService::class.java,
            ScreenRecorderService::class.java,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) LosslessRecorderService::class.java else null
        ).forEach {
            runCatching {
                context.stopService(Intent(context, it))
            }
        }
        ContextCompat.startForegroundService(context, intent)

        if (Preferences.prefs.getBoolean(Preferences.autoBackOnRecordingStartKey, false)) {
            (context as? Activity)?.moveTaskToBack(true)
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        startElapsedTimeCounter()
        handler.postDelayed(this::updateAmplitude, 100)
    }

    fun stopRecording() {
        val service = recorderService
        if (service is LosslessRecorderService) {
            isSaving = true
        }
        service?.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) canvasOverlay?.remove()
        recordedTime = null
        recordedAmplitudes.clear()
    }

    /** Scans for recordings interrupted by a crash / interrupted save. */
    fun checkPendingRecordings(context: Context) {
        pendingRecordings = WavFinalizer.findPending(context)
    }

    /** Moves the interrupted recordings into the output directory. */
    fun savePendingRecordings(context: Context) {
        val pending = pendingRecordings.toList()
        val appContext = context.applicationContext
        (appContext as App).appScope.launch {
            isSaving = true
            var saved = 0
            withContext(Dispatchers.IO) {
                pending.forEach {
                    val ok = runCatching {
                        WavFinalizer.finalizeToOutput(appContext, it.file, recovered = true)
                    }.getOrNull()
                    if (ok != null) saved++
                }
            }
            pendingRecordings = emptyList()
            isSaving = false
            markSaveDone(ok = saved > 0, name = null)
        }
    }
    /** Deletes the interrupted recordings. */
    fun discardPendingRecordings(context: Context) {
        val count = pendingRecordings.size
        pendingRecordings.forEach { WavFinalizer.discardPending(it.file) }
        pendingRecordings = emptyList()
        Toast.makeText(
            context,
            context.getString(R.string.discarded_pending, count),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Shows the finished-save banner for a moment, then removes it by itself. */
    private fun markSaveDone(ok: Boolean, name: String?) {
        saveDone = SaveOutcome(ok, name)
        saveDoneRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { saveDone = null; saveDoneRunnable = null }
        saveDoneRunnable = runnable
        handler.postDelayed(runnable, saveDoneDismissMs)
    }

    /** Shows the finished-save banner for a moment, then removes it by itself. */
    private fun markSaveDone(name: String?) {
        saveDone = SaveOutcome(ok = name != null, name = name)
        saveDoneRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable { saveDone = null; saveDoneRunnable = null }
        saveDoneRunnable = runnable
        handler.postDelayed(runnable, saveDoneDismissMs)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun pauseRecording() {
        recorderService?.pause()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun resumeRecording() {
        recorderService?.resume()
        handler.postDelayed(this::updateTime, 1000)
        if (recorderService is AudioRecorderService) {
            handler.postDelayed(this::updateAmplitude, 100)
        }
    }

    private fun updateTime() {
        if (recorderState != RecorderState.ACTIVE) return

        recordedTime = recordedTime?.plus(1)
        handler.postDelayed(this::updateTime, 100)
    }

    private fun updateAmplitude() {
        if (recorderState != RecorderState.ACTIVE) return

        recorderService?.getCurrentAmplitude()?.let {
            if (recordedAmplitudes.size >= 90) recordedAmplitudes.removeAt(0)
            recordedAmplitudes.add(it)
        }

        handler.postDelayed(this::updateAmplitude, 100)
    }

    private fun startElapsedTimeCounter() {
        recordedTime = 0L
        handler.postDelayed(this::updateTime, 100)
    }

    @SuppressLint("NewApi")
    fun hasScreenRecordingPermissions(context: Context): Boolean {
        val requiredPermissions = arrayListOf<String>()

        val recordAudio =
            Preferences.prefs.getInt(Preferences.audioSourceKey, 0) == AudioSource.MICROPHONE.value

        if (recordAudio) requiredPermissions.add(Manifest.permission.RECORD_AUDIO)

        val internalAudio = Preferences.prefs.getInt(
            Preferences.audioDeviceSourceKey,
            0
        ) == AudioDeviceSource.REMOTE_SUBMIX.value
        if (internalAudio) requiredPermissions.add(Manifest.permission.CAPTURE_AUDIO_OUTPUT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (requiredPermissions.isEmpty()) return true

        val granted = PermissionHelper.checkPermissions(context, requiredPermissions.toTypedArray())
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.no_sufficient_permissions), Toast.LENGTH_SHORT
            )
                .show()
        }
        return granted
    }
}
