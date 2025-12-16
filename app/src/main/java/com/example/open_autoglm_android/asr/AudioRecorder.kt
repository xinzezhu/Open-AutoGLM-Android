package com.example.open_autoglm_android.asr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioRecorder(
    private val sampleRate: Int = 16000
) {

    @Volatile
    private var isRecording: Boolean = false

    private var audioRecord: AudioRecord? = null
    private val recordedData = mutableListOf<Short>()

    fun start() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(sampleRate / 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        recordedData.clear()
        isRecording = true

        audioRecord?.startRecording()

        Thread {
            val buffer = ShortArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(recordedData) {
                        for (i in 0 until read) {
                            recordedData.add(buffer[i])
                        }
                    }
                }
            }
        }.start()
    }

    fun stop(): ShortArray {
        if (!isRecording) return ShortArray(0)
        isRecording = false

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        return synchronized(recordedData) {
            recordedData.toShortArray()
        }
    }

    fun getSampleRate(): Int = sampleRate
}


