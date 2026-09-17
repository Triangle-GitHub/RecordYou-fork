package com.bnyro.recorder.ui.screens

import android.view.SoundEffectConstants
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bnyro.recorder.App
import com.bnyro.recorder.R
import com.bnyro.recorder.enums.RecorderState
import com.bnyro.recorder.enums.RecorderType
import com.bnyro.recorder.ui.Destination
import com.bnyro.recorder.ui.common.ClickableIcon
import com.bnyro.recorder.ui.models.RecorderModel
import com.bnyro.recorder.util.WavFinalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    initialRecorder: RecorderType,
    onNavigate: (Destination) -> Unit,
    recorderModel: RecorderModel = viewModel(LocalContext.current as ComponentActivity)
) {
    val pagerState =
        rememberPagerState(initialPage = if (initialRecorder == RecorderType.VIDEO) 1 else 0) { 2 }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    var showRecoveryDialog by remember { mutableStateOf(false) }

    // Look for interrupted recordings (crash / interrupted save) and silently repair
    // any WAV files whose length fields were left stale by an interrupted copy.
    LaunchedEffect(Unit) {
        (context.applicationContext as App).appScope.launch {
            withContext(Dispatchers.IO) {
                WavFinalizer.repairBrokenWavs(context)
            }
            recorderModel.checkPendingRecordings(context)
            if (!recorderModel.isSaving && recorderModel.pendingRecordings.isNotEmpty()) {
                showRecoveryDialog = true
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
            ClickableIcon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings)
            ) {
                onNavigate(Destination.Settings)
            }
            ClickableIcon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = stringResource(R.string.recordings)
            ) {
                onNavigate(Destination.RecordingPlayer)
            }
        })
    }, bottomBar = {
        Column {
            AnimatedVisibility(recorderModel.recorderState == RecorderState.IDLE) {
                NavigationBar {
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(
                                    id = R.string.record_sound
                                )
                            )
                        },
                        label = { Text(stringResource(R.string.record_sound)) },
                        selected = (pagerState.currentPage == 0),
                        onClick = {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = stringResource(
                                    id = R.string.record_screen
                                )
                            )
                        },
                        label = { Text(stringResource(R.string.record_screen)) },
                        selected = (pagerState.currentPage == 1),
                        onClick = {
                            view.playSoundEffect(SoundEffectConstants.CLICK)
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        }
                    )
                }
            }
        }
    }) { paddingValues ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val saveFeedback = recorderModel.saveDone
            AnimatedVisibility(visible = recorderModel.isSaving || saveFeedback != null) {
                val ok = saveFeedback?.ok ?: true
                val container = if (ok) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
                val content = if (ok) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }
                Surface(
                    color = container,
                    contentColor = content,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (recorderModel.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.saving_recording),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Icon(
                                imageVector = if (ok) Icons.Default.Check else Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val name = saveFeedback?.name
                            val text = if (ok && name != null) {
                                stringResource(R.string.saved_recording, name)
                            } else {
                                stringResource(R.string.save_failed)
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { index ->
                RecorderView(recordScreenMode = (index == 1))
            }
        }
    }

    if (showRecoveryDialog && recorderModel.pendingRecordings.isNotEmpty()) {
        val count = recorderModel.pendingRecordings.size
        val totalMb = recorderModel.pendingRecordings.sumOf { it.sizeMb }
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text(stringResource(R.string.unfinished_recordings_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.unfinished_recordings_message,
                        count,
                        String.format("%.1f MB", totalMb)
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    recorderModel.savePendingRecordings(context)
                    showRecoveryDialog = false
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    recorderModel.discardPendingRecordings(context)
                    showRecoveryDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            }
        )
    }
}