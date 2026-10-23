package io.github.peanutpersimmon.rilo.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface VoiceAudioRecorder {
    suspend fun start(onChunk: (ByteArray) -> Unit, onFailure: (Throwable) -> Unit)
    suspend fun stop()
    fun cancel()
}

/** Captures raw PCM only. No files, network client, parser, or database dependency. */
class AndroidVoiceAudioRecorder : VoiceAudioRecorder {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var active: AudioRecord? = null
    private var readJob: Job? = null
    @Volatile private var recording = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @SuppressLint("MissingPermission")
    override suspend fun start(onChunk: (ByteArray) -> Unit, onFailure: (Throwable) -> Unit) {
        withContext(Dispatchers.IO) {
            synchronized(lock) { check(active == null) { "AudioRecord already active" } }
            val minimum = AudioRecord.getMinBufferSize(16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Microphone format unavailable" }
            val bufferSize = maxOf(minimum, 3_200)
            val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            synchronized(lock) { active = recorder }
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
                recording = true
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
                readJob = scope.launch {
                    val buffer = ByteArray(bufferSize)
                    try {
                        while (recording) {
                            val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                            if (count > 0) onChunk(buffer.copyOf(count))
                            else if (count < 0 && recording) error("AudioRecord read failed")
                        }
                    } catch (error: Throwable) {
                        if (recording) onFailure(error)
                    } finally { release(recorder) }
                }
            } catch (error: Throwable) {
                release(recorder)
                throw error
            }
        }
    }

    override suspend fun stop() {
        val (recorder, job) = synchronized(lock) {
            recording = false
            active to readJob
        }
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        job?.join()
        recorder?.let(::release)
    }

    override fun cancel() {
        val (recorder, job) = synchronized(lock) {
            recording = false
            active to readJob
        }
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        job?.cancel()
        recorder?.let(::release)
    }

    private fun release(recorder: AudioRecord) {
        val shouldRelease = synchronized(lock) {
            if (active !== recorder) false
            else { active = null; readJob = null; recording = false; true }
        }
        if (shouldRelease) recorder.release()
    }
}
