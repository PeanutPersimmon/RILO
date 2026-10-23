package io.github.peanutpersimmon.rilo.domain.voice.agent

/** Wire operations only; the recorder and the RILO executor have no access to each other. */
interface AudioChunkSink {
    fun append(pcm: ByteArray): Boolean
    fun commit(): Boolean
    fun responseCreate(): Boolean
    fun clear()
    fun close()
}

enum class AudioTurnIssue { Empty, TooLong, UploadFailed }

/** One manual, push-to-talk turn. PCM is kept only in this bounded memory queue until session.updated. */
class QwenAudioTurnController(
    private val maxBytes: Long = 16_000L * 2 * 60,
    private val chunkBytes: Int = 3_200,
) {
    init { require(maxBytes > 0 && chunkBytes > 0 && chunkBytes % 2 == 0) }

    private val queued = ArrayDeque<ByteArray>()
    private var sink: AudioChunkSink? = null
    private var ready = false
    private var released = false
    private var committed = false
    private var responseRequested = false
    private var cancelled = false
    private var issue: AudioTurnIssue? = null
    var bytesCaptured: Long = 0; private set
    var bytesSent: Long = 0; private set
    val isCancelled: Boolean get() = synchronized(this) { cancelled }
    val bufferedBytes: Int get() = synchronized(this) { queued.sumOf { it.size } }
    val failure: AudioTurnIssue? get() = synchronized(this) { issue }

    @Synchronized fun attach(value: AudioChunkSink) {
        if (cancelled) { value.close(); return }
        sink = value
    }

    @Synchronized fun append(pcm: ByteArray): AudioTurnIssue? {
        if (cancelled || released) return issue
        if (pcm.isEmpty()) return null
        if (bytesCaptured + pcm.size > maxBytes) return fail(AudioTurnIssue.TooLong)
        bytesCaptured += pcm.size
        var offset = 0
        while (offset < pcm.size) {
            val end = minOf(offset + chunkBytes, pcm.size)
            val chunk = pcm.copyOfRange(offset, end)
            if (ready) {
                if (sink?.append(chunk) != true) return fail(AudioTurnIssue.UploadFailed)
                bytesSent += chunk.size
            } else queued.addLast(chunk)
            offset = end
        }
        return null
    }

    @Synchronized fun sessionUpdated(): AudioTurnIssue? {
        if (cancelled) return issue
        ready = true
        while (queued.isNotEmpty()) {
            val chunk = queued.removeFirst()
            if (sink?.append(chunk) != true) return fail(AudioTurnIssue.UploadFailed)
            bytesSent += chunk.size
        }
        if (released) return commitIfReady()
        return null
    }

    @Synchronized fun finishRecording(): AudioTurnIssue? {
        if (cancelled || released) return issue
        released = true
        if (bytesCaptured == 0L) return fail(AudioTurnIssue.Empty)
        return commitIfReady()
    }

    @Synchronized fun bufferCommitted(): AudioTurnIssue? {
        if (cancelled || !committed || responseRequested) return issue
        responseRequested = true
        if (sink?.responseCreate() != true) return fail(AudioTurnIssue.UploadFailed)
        return null
    }

    @Synchronized fun cancel() {
        if (cancelled) return
        cancelled = true
        queued.clear()
        if (ready && !committed) sink?.clear()
        sink?.close()
    }

    @Synchronized private fun commitIfReady(): AudioTurnIssue? {
        if (!ready || committed || cancelled) return issue
        committed = true
        if (sink?.commit() != true) return fail(AudioTurnIssue.UploadFailed)
        return null
    }

    private fun fail(reason: AudioTurnIssue): AudioTurnIssue {
        issue = reason
        cancel()
        return reason
    }
}
