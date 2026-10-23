package io.github.peanutpersimmon.rilo.data.remote.qwen

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.domain.voice.agent.AudioChunkSink
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenAudioTurnController
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenCredentials
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceAgentDiagnosticsStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

interface QwenAudioClient {
    suspend fun executeAudio(credentials: QwenCredentials, turn: QwenAudioTurnController,
        context: VoiceAgentContext): QwenRemoteResult
}

/** One WebSocket per push-to-talk command. Only the shared Qwen tool result is returned. */
class QwenRealtimeAudioClient(
    providedHttp: OkHttpClient? = null,
    private val gson: Gson = Gson(),
    private val timeoutMillis: Long = 90_000,
) : QwenAudioClient {
    private val http: OkHttpClient by lazy {
        providedHttp ?: OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    }
    override suspend fun executeAudio(credentials: QwenCredentials, turn: QwenAudioTurnController,
        context: VoiceAgentContext): QwenRemoteResult {
        if (credentials.apiKey.isBlank() || !Regex("[A-Za-z0-9_-]{1,128}").matches(credentials.workspaceId))
            return QwenRemoteResult.Failed("千问尚未配置")
        val response = CompletableDeferred<QwenRemoteResult>()
        val outputFinished = CompletableDeferred<Unit>()
        val socketRef = AtomicReference<WebSocket?>()
        val returnedTool = AtomicBoolean(false)
        val initialResponseDone = AtomicBoolean(false)
        val outputSent = AtomicBoolean(false)
        val sink = object : AudioChunkSink {
            private fun send(value: JsonObject): Boolean = socketRef.get()?.send(gson.toJson(value)) == true
            override fun append(pcm: ByteArray) = send(QwenAgentProtocol.audioAppend(pcm))
            override fun commit() = send(QwenAgentProtocol.audioCommit())
            override fun responseCreate() = send(QwenAgentProtocol.responseCreate())
            override fun clear() { send(QwenAgentProtocol.audioClear()) }
            override fun close() { socketRef.get()?.close(1000, "cancelled") }
        }
        turn.attach(sink)
        if (turn.isCancelled) return QwenRemoteResult.Failed("语音输入已取消")
        val request = Request.Builder().url(
            "wss://${credentials.workspaceId}.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=${QwenAgentProtocol.MODEL}")
            .header("Authorization", "Bearer ${credentials.apiKey}").build()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                socketRef.set(webSocket)
                if (turn.isCancelled) webSocket.cancel()
                else VoiceAgentDiagnosticsStore.event("audio connection = success")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { JsonParser.parseString(text).asJsonObject } catch (_: Exception) {
                    if (!response.isCompleted) response.complete(QwenRemoteResult.Failed("千问语音响应无效"))
                    turn.cancel()
                    return
                }
                when (event.string("type")) {
                    "session.created" -> {
                        VoiceAgentDiagnosticsStore.event("audio session created")
                        if (!webSocket.send(gson.toJson(QwenAgentProtocol.sessionUpdate(context, audioInput = true)))) {
                            if (!response.isCompleted) response.complete(QwenRemoteResult.Failed("无法配置语音会话"))
                            turn.cancel()
                        }
                    }
                    "session.updated" -> {
                        VoiceAgentDiagnosticsStore.event("audio session updated")
                        val issue = turn.sessionUpdated()
                        VoiceAgentDiagnosticsStore.event("audio bytes sent = ${turn.bytesSent}")
                        if (issue != null && !response.isCompleted)
                            response.complete(QwenRemoteResult.Failed("音频上传失败"))
                    }
                    "input_audio_buffer.committed" -> {
                        VoiceAgentDiagnosticsStore.event("audio committed = true")
                        if (turn.bufferCommitted() != null && !response.isCompleted)
                            response.complete(QwenRemoteResult.Failed("无法提交语音命令"))
                    }
                    "response.function_call_arguments.done" -> {
                        if (!returnedTool.compareAndSet(false, true)) return
                        val callId = event.string("call_id")
                        val name = event.string("name")
                        val arguments = event.string("arguments")
                        if (callId == null || name == null || arguments == null) {
                            response.complete(QwenRemoteResult.InvalidToolCall("千问工具调用缺少必要字段"))
                            turn.cancel()
                            return
                        }
                        VoiceAgentDiagnosticsStore.event("audio tool called = $name")
                        response.complete(QwenRemoteResult.ToolCall(callId, name, arguments, { output ->
                            outputSent.set(true)
                            val sent = webSocket.send(gson.toJson(QwenAgentProtocol.functionOutput(callId, output))) &&
                                webSocket.send(gson.toJson(QwenAgentProtocol.responseCreate()))
                            if (sent) try { withTimeout(5_000) { outputFinished.await() } }
                                catch (_: TimeoutCancellationException) {
                                    VoiceAgentDiagnosticsStore.event("audio tool output response timeout")
                                }
                            webSocket.close(1000, "completed")
                        }, { webSocket.cancel() }))
                    }
                    "response.done" -> {
                        if (initialResponseDone.compareAndSet(false, true)) {
                            if (!returnedTool.get() && !response.isCompleted) {
                                response.complete(QwenRemoteResult.NoToolCall)
                                webSocket.close(1000, "no tool")
                            }
                        } else if (outputSent.get()) outputFinished.complete(Unit)
                    }
                    "error" -> {
                        VoiceAgentDiagnosticsStore.event("audio API error")
                        if (!response.isCompleted) response.complete(QwenRemoteResult.Failed("千问语音服务错误"))
                        else outputFinished.complete(Unit)
                        if (!returnedTool.get()) turn.cancel()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, responseInfo: Response?) {
                VoiceAgentDiagnosticsStore.event("audio socket failure = ${error.javaClass.simpleName}")
                if (!response.isCompleted) response.complete(QwenRemoteResult.Failed("千问语音连接失败"))
                outputFinished.complete(Unit)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!response.isCompleted) response.complete(QwenRemoteResult.Failed("千问语音连接已关闭"))
                outputFinished.complete(Unit)
            }
        })
        socketRef.set(socket)
        if (turn.isCancelled) socket.cancel()
        return try {
            withTimeout(timeoutMillis) { response.await() }
        } catch (_: TimeoutCancellationException) {
            VoiceAgentDiagnosticsStore.event("audio timeout")
            turn.cancel()
            QwenRemoteResult.Failed("千问语音请求超时")
        } catch (error: CancellationException) {
            turn.cancel()
            throw error
        }
    }

    private fun JsonObject.string(name: String): String? = try {
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    } catch (_: Exception) { null }
}
