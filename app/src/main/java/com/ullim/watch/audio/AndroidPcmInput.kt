package com.ullim.watch.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ullim.watch.core.AudioFrame

class AndroidPcmInput private constructor(private val recorder: AudioRecord) : PcmInput {
    override fun start() {
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
    }

    override fun read(buffer: ShortArray): Int = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
    override fun stop() {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
    }
    override fun release() { recorder.release() }

    companion object {
        @SuppressLint("MissingPermission")
        fun create(context: Context): AndroidPcmInput {
            val minBytes = AudioRecord.getMinBufferSize(AudioFrame.SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBytes > 0)
            val format = AudioFormat.Builder().setSampleRate(AudioFrame.SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            val unprocessed = context.getSystemService(AudioManager::class.java)
                .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val sources = if (unprocessed) listOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.VOICE_RECOGNITION)
                else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            for (source in sources) {
                val recorder = try {
                    AudioRecord.Builder().setAudioSource(source).setAudioFormat(format)
                        .setBufferSizeInBytes(maxOf(minBytes, AudioFrame.DSP_SAMPLES * 8)).build()
                } catch (_: IllegalArgumentException) { continue }
                if (recorder.state == AudioRecord.STATE_INITIALIZED) return AndroidPcmInput(recorder)
                recorder.release()
            }
            error("Microphone initialization failed")
        }
    }
}
