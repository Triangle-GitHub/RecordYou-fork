package com.bnyro.recorder.util

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.bnyro.recorder.App
import java.io.File
import java.io.FileInputStream
import java.io.IOException


/**
 * Moves finished / interrupted WAV recordings from the app's private temp area into the
 * user-selected output directory, and locates interrupted recordings after a crash.
 *
 * Pending files are kept as valid WAV containers (placeholder header + periodically
 * patched lengths), so finalizing is a plain file move - or a buffered copy when the
 * destination lives behind a DocumentsProvider that cannot be renamed into.
 */
object WavFinalizer {
    private const val TAG = "WavFinalizer"
    private const val PENDING_PREFIX = "pending_"
    private const val RECOVERED_PREFIX = "Recovered_"
    private const val COPY_BUFFER_SIZE = 256 * 1024
    private const val ABSENT_RECENTLY_MS = 2_000L

    data class PendingRecording(val file: File, val sizeBytes: Long) {
        val sizeMb: Double
            get() = sizeBytes / 1024.0 / 1024.0
    }

    /** Directory holding pending recordings for the current output location. */
    fun pendingDir(context: Context): File {
        val outputDir = (context.applicationContext as App).fileRepository.getOutputDir()
        val path = outputDir.uri.path
        return if (outputDir.uri.scheme == "file" && path != null) {
            File(path, "tmp").apply { mkdirs() }
        } else {
            File(context.filesDir, "tmp").apply { mkdirs() }
        }
    }

    fun createPendingFile(context: Context): File =
        File(pendingDir(context), "${PENDING_PREFIX}${System.currentTimeMillis()}.wav")

    /** Finds pending recordings left behind by a crash or an interrupted save. */
    fun findPending(context: Context): List<PendingRecording> {
        val roots = mutableSetOf<File>()
        roots += File(context.filesDir, "tmp")
        context.getExternalFilesDir(null)?.let { roots += File(it, "tmp") }
        val now = System.currentTimeMillis()
        return roots
            .filter { it.isDirectory }
            .flatMap { dir ->
                dir.listFiles()?.filter { file ->
                    file.isFile &&
                        file.name.startsWith(PENDING_PREFIX) &&
                        file.name.endsWith(".wav") &&
                        file.length() >= PcmConverter.HEADER_SIZE
                } ?: emptyList()
            }
            .sortedByDescending { it.name }
            .map { PendingRecording(it, it.length()) }
            .filter { now - it.file.lastModified() > ABSENT_RECENTLY_MS }
    }

    /**
     * Moves a pending WAV into the output directory. Uses an atomic rename when the
     * destination is a real path the app owns, otherwise falls back to a buffered copy.
     * Returns the destination document, or null on failure.
     */
    fun finalizeToOutput(context: Context, pending: File, recovered: Boolean = false): DocumentFile? {
        val repo = (context.applicationContext as App).fileRepository
        val outputDir = repo.getOutputDir()
        val dest = repo.getUniqueOutputFile("wav", if (recovered) RECOVERED_PREFIX else "")
            ?: return null

        return try {
            if (outputDir.uri.scheme == "file" && outputDir.uri.path != null) {
                val destName = dest.name ?: return null
                if (pending.renameTo(File(outputDir.uri.path, destName))) {
                    return dest
                }
            }
            // DocumentsProvider destination: stream-copy, then drop the temp file.
            context.contentResolver.openOutputStream(dest.uri, "w")?.use { out ->
                FileInputStream(pending).use { input ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                }
            } ?: return null
            if (!pending.delete()) pending.deleteOnExit()
            dest
        } catch (e: IOException) {
            Log.e(TAG, "Failed to finalize ${pending.name}", e)
            null
        }
    }

    fun discardPending(pending: File) {
        if (!pending.delete()) pending.deleteOnExit()
    }

    /**
     * Repairs WAV files in the output directories whose length fields no longer match
     * the actual file size (e.g. a save that was interrupted mid-copy). Cheap and safe:
     * untouched files already carry the correct lengths and are left alone.
     */
    private val repairedOnce = java.util.concurrent.atomic.AtomicBoolean(false)

    fun repairBrokenWavs(context: Context) {
        if (!repairedOnce.compareAndSet(false, true)) return
        val repo = (context.applicationContext as App).fileRepository
        repo.getOutputDirs().forEach { dir ->
            dir.listFiles()
                .filter { it.isFile && it.name?.endsWith(".wav") == true }
                .forEach file@{ wav ->
                    if (wav.length() < PcmConverter.HEADER_SIZE) {
                        return@file
                    }
                    val actual = wav.length() - PcmConverter.HEADER_SIZE
                    try {
                        context.contentResolver.openFileDescriptor(wav.uri, "r")?.use { pfd ->
                            val declared = PcmConverter.readDataSize(pfd.fileDescriptor)
                            if (declared != actual) {
                                context.contentResolver.openFileDescriptor(wav.uri, "rw")?.use { rw ->
                                    PcmConverter.patchLengths(rw.fileDescriptor, actual)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipped repairing ${wav.name}: ${e.message}")
                    }
                }
        }
    }

}