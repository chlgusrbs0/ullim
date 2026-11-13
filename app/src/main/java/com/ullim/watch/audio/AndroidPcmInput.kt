package com.ullim.watch.audio

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import com.ullim.watch.core.AudioFrame

class AndroidPcmInput private constructor(private val recorder: AudioRecord) : PcmInput {
    private val availability = RecordingAvailability()
    private var registered = false
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configurations: List<AudioRecordingConfiguration>) {
            configurations.firstOrNull { it.clientAudioSessionId == recorder.audioSessionId }
                ?.let { availability.update(it.isClientSilenced) }
        }
    }
    override fun start() {
        recorder.registerAudioRecordingCallback({ it.run() }, recordingCallback)
        registered = true
        recorder.startRecording()
        check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        availability.initialize(recorder.activeRecordingConfiguration?.isClientSilenced == true)
        availability.checkReadable()
    }

    override fun read(buffer: ShortArray): Int {
        availability.checkReadable()
        val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        availability.checkReadable()
        return count
    }
    override fun stop() {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
    }
    override fun release() {
        try { if (registered) recorder.unregisterAudioRecordingCallback(recordingCallback) }
        finally { registered = false; recorder.release() }
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun create(context: Context): AndroidPcmInput {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Microphone permission unavailable")
            }
            val minBytes = AudioRecord.getMinBufferSize(AudioFrame.SAMPLE_RATE_HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBytes > 0)
            val format = AudioFormat.Builder().setSampleRate(AudioFrame.SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
            val unprocessed = context.getSystemService(AudioManager::class.java)
                .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            return openPcmInput(unprocessed,
                { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            ) { source ->
                val nativeSource = when (source) {
                    PcmSource.UNPROCESSED -> MediaRecorder.AudioSource.UNPROCESSED
                    PcmSource.VOICE_RECOGNITION -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                }
                val recorder = AudioRecord.Builder().setAudioSource(nativeSource).setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(minBytes, AudioFrame.DSP_SAMPLES * 8)).build()
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    throw IllegalArgumentException("Microphone was not initialized")
                }
                AndroidPcmInput(recorder)
            }
        }
    }
}
