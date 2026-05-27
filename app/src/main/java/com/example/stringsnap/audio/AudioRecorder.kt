package com.example.stringsnap.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

class AudioRecorder(
    val sampleRateHz: Int = 44_100,
    private val frameSize: Int = 8_192
) {
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    @Suppress("MissingPermission")
    fun start(onFrame: (ShortArray, Int) -> Unit) {
        if (running) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, frameSize * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord = recorder
        running = true
        recorder.startRecording()

        worker = thread(name = "StringSnapAudioRecorder") {
            val buffer = ShortArray(frameSize)
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onFrame(buffer.copyOf(read), read)
                }
            }
        }
    }

    fun stop() {
        running = false
        worker?.join(300)
        worker = null
        audioRecord?.run {
            runCatching { stop() }
            release()
        }
        audioRecord = null
    }
}
