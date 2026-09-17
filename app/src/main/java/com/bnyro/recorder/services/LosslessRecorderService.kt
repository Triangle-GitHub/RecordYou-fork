package com.bnyro.recorder.services

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.bnyro.recorder.App
import com.bnyro.recorder.R
import com.bnyro.recorder.enums.RecorderState
import com.bnyro.recorder.util.PcmConverter
import com.bnyro.recorder.util.WavFinalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Lossless (raw PCM) recorder.
 *
 * Leaves a valid WAV at every moment: a placeholder header is written first and the
 * length fields are patched periodically, so even an interrupted recording stays a
 * recognizable, playable file that can simply be moved to the output directory later.
 */
@RequiresApi(Build.VERSION_CODES.M)
class LosslessRecorderService : RecorderService() {
    override val notificationTitle: String
        get() = getString(R.string.recording_audio)

    override val fgServiceType: Int?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            null
        }

    private var audioRecorder: AudioRecord? = null
    private var recorderThread: Thread? = null
    private var pendingFile: File? = null
    private var currentMaxAmplitude: Int? = null

    private val pcmConverter = PcmConverter(
        SAMPLING_RATE.toLong(),
        CHANNEL_COUNT,
        16
    )

    /** Requests the recording thread to refresh the WAV header lengths (pause/stop). */
    private val patchRequested = AtomicBoolean(false)

    /** Guards [onDestroy] so a repeated call (UI, notification, framework) runs the save once. */
    private var finalizeStarted = false

    @SuppressLint("MissingPermission")
    override fun start() {
        super.start()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioFormat: AudioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLING_RATE)
                .setChannelMask(CHANNEL_IN)
                .setEncoding(FORMAT)
                .build()

            audioRecorder = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                audioFormat.sampleRate,
                audioFormat.channelMask,
                audioFormat.encoding,
                BUFFER_SIZE_IN_BYTES
            )
        }

        val pending = WavFinalizer.createPendingFile(this)
        pendingFile = pending
        try {
            BufferedOutputStream(FileOutputStream(pending), COPY_BUFFER_SIZE).use { out ->
                pcmConverter.writeHeader(out)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to create pending recording", e)
            onDestroy()
            return
        }

        audioRecorder?.startRecording()

        recorderThread = thread(true) {
            writeAudioDataToFile()
        }
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(BUFFER_SIZE_IN_BYTES / 2)
        var bytesWritten = 0L
        var chunksSincePatch = 0
        val pending = pendingFile ?: run {
            Log.e(TAG, "No pending file - aborting recording thread")
            return
        }
        try {
            // Append: the placeholder header was already written in start().
            BufferedOutputStream(FileOutputStream(pending, true), COPY_BUFFER_SIZE).use { out ->
                while (recorderState != RecorderState.IDLE) {
                    val read = audioRecorder?.read(data, 0, data.size) ?: 0
                    if (read > 0) {
                        if (recorderState == RecorderState.ACTIVE) {
                            out.write(data, 0, read)
                            bytesWritten += read
                            currentMaxAmplitude = maxAmplitude(data, read)
                        }
                        chunksSincePatch++
                    } else {
                        // Recorder stopped (paused or finishing) - avoid busy-spinning.
                        Thread.sleep(50)
                    }
                    if (chunksSincePatch >= CHUNKS_PER_PATCH || patchRequested.getAndSet(false)) {
                        out.flush()
                        PcmConverter.patchLengths(pending, bytesWritten)
                        chunksSincePatch = 0
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write audio data", e)
        }
    }

    /** Peak amplitude over the first [length] bytes, without allocating per chunk. */
    private fun maxAmplitude(bytes: ByteArray, length: Int): Int {
        var max = 0
        var i = 0
        while (i < length - 1) {
            val sample = (((bytes[i + 1].toInt() and 0xFF) shl 8) or (bytes[i].toInt() and 0xFF)).toShort().toInt()
            val abs = if (sample < 0) -sample else sample
            if (abs > max) max = abs
            i += 2
        }
        return max
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun pause() {
        super.pause()
        audioRecorder?.stop()
        // Seal the WAV header at the pause point so the file is complete up to here.
        patchRequested.set(true)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun resume() {
        super.resume()
        audioRecorder?.startRecording()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        if (finalizeStarted) return
        finalizeStarted = true

        runCatching {
            recorderState = RecorderState.IDLE
            onRecorderStateChanged(recorderState)
        }
        cancelRecordingNotification()
        onSaveStateChanged(true, null)

        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null
        val recordingThread = recorderThread
        recorderThread = null
        val pending = pendingFile

        (application as App).appScope.launch {
            withContext(Dispatchers.IO) {
                // Ensure all PCM was flushed to the pending file before moving it.
                recordingThread?.join(5_000)

                if (pending != null && pending.exists() && pending.length() > PcmConverter.HEADER_SIZE) {
                    val audioLength = pending.length() - PcmConverter.HEADER_SIZE
                    PcmConverter.patchLengths(pending, audioLength)
                    outputFile = WavFinalizer.finalizeToOutput(this@LosslessRecorderService, pending)
                } else {
                    runCatching { pending?.delete() }
                }
            }
            withContext(Dispatchers.Main) {
                onSaveStateChanged(false, outputFile?.name)
            }
            cleanupAndStop()
        }
    }

    override fun getCurrentAmplitude() = currentMaxAmplitude

    companion object {
        private const val TAG = "LosslessRecorderService"
        private const val SAMPLING_RATE = 44100
        private const val CHANNEL_COUNT = 2
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_STEREO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val COPY_BUFFER_SIZE = 256 * 1024

        /** Patch the WAV header roughly every 5 seconds of audio (at 44.1kHz stereo). */
        private const val CHUNKS_PER_PATCH = 64

        private val BUFFER_SIZE_IN_BYTES = 2 * AudioRecord.getMinBufferSize(
            SAMPLING_RATE,
            CHANNEL_IN,
            FORMAT
        )
    }
}