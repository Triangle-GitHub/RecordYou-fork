package com.bnyro.recorder.util

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.bnyro.recorder.enums.RecorderType
import com.bnyro.recorder.enums.SortOrder
import com.bnyro.recorder.obj.RecordingItemData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar

interface FileRepository {
    suspend fun getVideoRecordingItems(sortOrder: SortOrder): List<RecordingItemData>
    suspend fun getAudioRecordingItems(sortOrder: SortOrder): List<RecordingItemData>
    suspend fun deleteFiles(files: List<DocumentFile>)
    suspend fun deleteAllFiles()
    fun getOutputFile(extension: String, prefix: String = ""): DocumentFile?
    fun getUniqueOutputFile(extension: String, prefix: String = ""): DocumentFile?
    fun getOutputDir(): DocumentFile
    fun getOutputDirs(): List<DocumentFile>
}

class FileRepositoryImpl(val context: Context) : FileRepository {
    private val commonAudioExtensions = listOf(
        ".mp3",
        ".aac",
        ".ogg",
        ".wma",
        ".3gp",
        ".wav",
        ".m4a"
    )

    private val commonVideoExtensions = listOf(
        ".mp4",
        ".mov",
        ".avi",
        ".mkv",
        ".webm",
        ".mpg"
    )

    private fun getVideoFiles(): List<DocumentFile> =
        getOutputDirs().flatMap {
            it.listFiles().filter { file ->
            file.isFile && commonVideoExtensions.any { file.name?.endsWith(it) ?: false }
        }
        }

    private fun getAudioFiles(): List<DocumentFile> =
        getOutputDirs().flatMap {
            it.listFiles().filter { file ->
            file.isFile && commonAudioExtensions.any { file.name?.endsWith(it) ?: false }
            }
        }

    override suspend fun getVideoRecordingItems(sortOrder: SortOrder): List<RecordingItemData> {
        val items = withContext(Dispatchers.IO) {
            getVideoFiles().sortedBy(sortOrder).map {
                val thumbnail =
                    kotlin.runCatching {
                        MediaMetadataRetriever().apply {
                            setDataSource(context, it.uri)
                        }.frameAtTime
                    }.getOrNull()
                RecordingItemData(it, RecorderType.VIDEO, thumbnail)
            }
        }
        return items
    }

    override suspend fun getAudioRecordingItems(sortOrder: SortOrder): List<RecordingItemData> {
        val items = withContext(Dispatchers.IO) {
            getAudioFiles().sortedBy(sortOrder).map { RecordingItemData(it, RecorderType.AUDIO) }
        }
        return items
    }

    override suspend fun deleteFiles(files: List<DocumentFile>) {
        withContext(Dispatchers.IO) {
            files.forEach {
                if (it.exists()) it.delete()
            }
        }
    }

    override suspend fun deleteAllFiles() {
        withContext(Dispatchers.IO) {
            getOutputDirs().map { files ->
                files.listFiles().forEach {
                if (it.isFile) it.delete()
            }
            }
        }
    }

    override fun getOutputFile(extension: String, prefix: String): DocumentFile? {
        val outputDir = getOutputDir()
        if (!outputDir.exists() || !outputDir.canRead() || !outputDir.canWrite()) return null

        val fullFileName = buildFileName(extension, prefix)
        val existingFile = outputDir.findFile(fullFileName)

        return existingFile ?: outputDir.createFile("audio/*", fullFileName)
    }

    /**
     * Like [getOutputFile] but never reuses an existing file: appends a millis suffix
     * until the generated name is free. Used when moving a finished recording into the
     * output directory without clobbering a previous (possibly interrupted) write.
     */
    override fun getUniqueOutputFile(extension: String, prefix: String): DocumentFile? {
        val outputDir = getOutputDir()
        if (!outputDir.exists() || !outputDir.canWrite()) return null
        var attempt = 0
        while (attempt < 10) {
            val suffix = if (attempt == 0) "" else "${System.currentTimeMillis()}_"
            val name = buildFileName(extension, "$prefix$suffix")
            if (outputDir.findFile(name) == null) {
                return outputDir.createFile("audio/*", name)
            }
            attempt++
        }
        return null
    }

    private fun buildFileName(extension: String, prefix: String): String {
        val time = Calendar.getInstance().time
        val currentDateTime = dateTimeFormat.format(time)
        val currentDate = currentDateTime.split("_").first()
        val currentTime = currentDateTime.split("_").last()

        val fileName = Preferences.getString(
            Preferences.namingPatternKey,
            DEFAULT_NAMING_PATTERN
        )
            .replace("%d", currentDate)
            .replace("%t", currentTime)
            .replace("%m", time.time.toString())
            .replace("%s", time.time.div(1000).toString())

        return "$prefix$fileName.$extension"
    }

    override fun getOutputDir(): DocumentFile = getOutputDirs().last()
    override fun getOutputDirs(): List<DocumentFile> {
        val prefDir = Preferences.prefs.getString(Preferences.targetFolderKey, "")
        val externalFilesDir = run {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            DocumentFile.fromFile(dir)
        }
        return when {
            prefDir.isNullOrBlank() -> listOf(externalFilesDir)
            else -> listOf(externalFilesDir, DocumentFile.fromTreeUri(context, prefDir.toUri())!!)
        }
    }

    companion object {
        @SuppressLint("SimpleDateFormat")
        private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
        const val DEFAULT_NAMING_PATTERN = "%d_%t"
    }
}

fun List<DocumentFile>.sortedBy(sortOrder: SortOrder): List<DocumentFile> {
    return when (sortOrder) {
        SortOrder.MODIFIED -> sortedBy { it.lastModified() }
        SortOrder.MODIFIED_REV -> sortedByDescending { it.lastModified() }
        SortOrder.ALPHABETIC -> sortedBy { it.name }
        SortOrder.ALPHABETIC_REV -> sortedByDescending { it.name }
        SortOrder.SIZE_REV -> sortedBy { it.length() }
        SortOrder.SIZE -> sortedByDescending { it.length() }
    }
}
