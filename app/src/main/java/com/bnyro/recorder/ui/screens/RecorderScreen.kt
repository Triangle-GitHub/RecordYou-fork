package com.bnyro.recorder.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bnyro.recorder.enums.RecorderState
import com.bnyro.recorder.ui.common.ResponsiveRecordScreenLayout
import com.bnyro.recorder.ui.components.RecorderController
import com.bnyro.recorder.ui.components.RecorderPreview
import com.bnyro.recorder.ui.models.RecorderModel
import com.bnyro.recorder.util.Preferences

@Composable
fun RecorderView(
    recordScreenMode: Boolean
) {
    val recorderModel: RecorderModel = viewModel(LocalContext.current as ComponentActivity)
    val context = LocalContext.current
    val mProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val requestRecording = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        recorderModel.startVideoRecorder(context, result)
    }

    fun startRecordingFlow() {
        if (recordScreenMode) {
            if (!recorderModel.hasScreenRecordingPermissions(context)) return
            requestRecording.launch(
                mProjectionManager.createScreenCaptureIntent()
            )
        } else {
            recorderModel.startAudioRecorder(context)
        }
    }

    val centerTapEnabled = recorderModel.recorderState == RecorderState.IDLE &&
        Preferences.prefs.getBoolean(Preferences.centerTapRecordKey, false)

    LaunchedEffect(recorderModel.recorderState) {
        // update the UI when the recorder gets destroyed by the notification
        if (recorderModel.recorderState == RecorderState.IDLE) {
            recorderModel.stopRecording()
        }
    }

    Scaffold { pV ->
        ResponsiveRecordScreenLayout(
            modifier = Modifier
                .fillMaxSize()
                .padding(pV),
            PaneOne = {
                RecorderPreview(
                    recordScreenMode,
                    onClick = if (centerTapEnabled) ::startRecordingFlow else null
                )
            },
            PaneTwo = {
                RecorderController(
                    onStartRecording = ::startRecordingFlow
                )
            }
        )
    }
}