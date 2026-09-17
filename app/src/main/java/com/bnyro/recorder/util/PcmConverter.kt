package com.bnyro.recorder.util

import android.util.Log
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Minimal WAV container handling for the lossless recorder.
 *
 * The recorder writes a placeholder header first and streams raw PCM right after it,
 * then patches the length fields periodically and once more when the recording ends.
 * Because the file stays a valid WAV at every moment, an interrupted recording is
 * still recognizable, playable up to the last patch and directly movable/recoverable
 * without any re-encoding.
 */
class PcmConverter(
    private val sampleRate: Long,
    private val channels: Int,
    private val bitsPerSample: Int
) {
    private val byteRate = channels * sampleRate * bitsPerSample / 8
    private val blockAlign = channels * bitsPerSample / 8

    /** Writes a placeholder WAV header. Call with the final [audioLength] to write a complete one. */
    fun writeHeader(outputStream: OutputStream, audioLength: Long = 0) {
        outputStream.write(buildHeader(audioLength, sampleRate, channels, bitsPerSample, byteRate, blockAlign))
    }

    /**
     * Converts raw PCM (with no header) into a complete WAV. [audioLength] must be the
     * exact number of PCM bytes in [inputStream] - usually obtained from the file length -
     * so the header is accurate regardless of how [inputStream] reports `available()`.
     */
    fun convertToWave(inputStream: InputStream, outputStream: OutputStream, bufferSize: Int, audioLength: Long) {
        val data = ByteArray(bufferSize)
        try {
            writeHeader(outputStream, audioLength)
            var total = 0L
            while (true) {
                val read = inputStream.read(data)
                if (read == -1) break
                outputStream.write(data, 0, read)
                total += read
            }
            if (total != audioLength) {
                Log.w(TAG, "Converted $total bytes, header claims $audioLength")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to convert to wav", e)
        }
    }

    companion object {
        private const val TAG = "PcmConverter"
        const val HEADER_SIZE = 44
        private const val FORMAT_PCM = 1

        /** RIFF size field lives at offset 4, the data chunk size at offset 40. */
        private const val RIFF_SIZE_OFFSET = 4
        private const val DATA_SIZE_OFFSET = 40

        fun buildHeader(
            audioLength: Long,
            sampleRate: Long,
            channels: Int,
            bitsPerSample: Int,
            byteRate: Long,
            blockAlign: Int
        ): ByteArray {
            val header = ByteArray(HEADER_SIZE)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            writeLeInt(header, RIFF_SIZE_OFFSET, audioLength + 36)
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            writeLeInt(header, 16, 16) // fmt chunk size
            writeLeShort(header, 20, FORMAT_PCM)
            writeLeShort(header, 22, channels)
            writeLeInt(header, 24, sampleRate)
            writeLeInt(header, 28, byteRate)
            writeLeShort(header, 32, blockAlign)
            writeLeShort(header, 34, bitsPerSample)
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            writeLeInt(header, DATA_SIZE_OFFSET, audioLength)
            return header
        }

        /** Patches the length fields of an existing WAV file opened via [fd]. */
        fun patchLengths(fd: FileDescriptor, audioLength: Long) {
            FileOutputStream(fd).use { fos ->
                val channel = fos.channel
                channel.position(RIFF_SIZE_OFFSET.toLong())
                channel.write(ByteBuffer.wrap(leIntBytes(audioLength + 36)))
                channel.position(DATA_SIZE_OFFSET.toLong())
                channel.write(ByteBuffer.wrap(leIntBytes(audioLength)))
            }
        }

        /** Patches the length fields of a WAV file on disk. */
        fun patchLengths(file: File, audioLength: Long) {
            RandomAccessFile(file, "rw").use { raf -> patchLengths(raf, audioLength) }
        }

        private fun patchLengths(raf: RandomAccessFile, audioLength: Long) {
            raf.seek(RIFF_SIZE_OFFSET.toLong())
            writeLeInt(raf, audioLength + 36)
            raf.seek(DATA_SIZE_OFFSET.toLong())
            writeLeInt(raf, audioLength)
        }

        /** Reads the declared data chunk size (offset 40) of a WAV file. */
        fun readDataSize(fd: FileDescriptor): Long {
            FileInputStream(fd).use { fis ->
                val channel = fis.channel
                channel.position(DATA_SIZE_OFFSET.toLong())
                val buffer = ByteBuffer.allocate(4)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) <= 0) break
                }
                val b = buffer.array()
                return (b[0].toLong() and 0xff) or
                    ((b[1].toLong() and 0xff) shl 8) or
                    ((b[2].toLong() and 0xff) shl 16) or
                    ((b[3].toLong() and 0xff) shl 24)
            }
        }

        private fun writeLeInt(out: ByteArray, offset: Int, value: Long) {
            out[offset] = (value and 0xffL).toByte()
            out[offset + 1] = ((value shr 8) and 0xffL).toByte()
            out[offset + 2] = ((value shr 16) and 0xffL).toByte()
            out[offset + 3] = ((value shr 24) and 0xffL).toByte()
        }

        private fun writeLeShort(out: ByteArray, offset: Int, value: Int) {
            out[offset] = (value and 0xff).toByte()
            out[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        private fun leIntBytes(value: Long): ByteArray = byteArrayOf(
            (value and 0xffL).toByte(),
            ((value shr 8) and 0xffL).toByte(),
            ((value shr 16) and 0xffL).toByte(),
            ((value shr 24) and 0xffL).toByte()
        )

        private fun writeLeInt(raf: RandomAccessFile, value: Long) {
            raf.write((value and 0xffL).toInt())
            raf.write(((value shr 8) and 0xffL).toInt())
            raf.write(((value shr 16) and 0xffL).toInt())
            raf.write(((value shr 24) and 0xffL).toInt())
        }
    }
}