package io.github.peanutpersimmon.rilo.data.remote.qwen

import com.google.gson.Gson
import com.google.gson.JsonParser
import io.github.peanutpersimmon.rilo.domain.voice.agent.*
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceAgentDiagnosticsStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface QwenRemoteResult {
    data class ToolCall(
        val callId: String,
        val name: String,
        val arguments: String,
        val sendOutput: suspend (String) -> Unit,
        val cancel: () -> Unit,
    ) : QwenRemoteResult
    data object NoToolCall : QwenRemoteResult
    data class InvalidToolCall(val message: String) : QwenRemoteResult
    data class Failed(val message: String) : QwenRemoteResult
}

sealed interface QwenConnectionResult {
    data object Success : QwenConnectionResult
    data class Failed(val reason: String) : QwenConnectionResult
}

interface QwenTextClient {
    suspend fun executeText(credentials: QwenCredentials, text: String, context: VoiceAgentContext): QwenRemoteResult
}

class QwenRealtimeClient(
    providedHttp: OkHttpClient? = null,
    private val gson: Gson = Gson(),
    private val timeoutMillis: Long = 25_000,
) : QwenTextClient {
    private val http: OkHttpClient by lazy {
        providedHttp ?: OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    }
    override suspend fun executeText(credentials: QwenCredentials, text: String,
        context: VoiceAgentContext): QwenRemoteResult {
        if (credentials.apiKey.isBlank() || credentials.workspaceId.isBlank())
            return QwenRemoteResult.Failed("千问尚未配置")
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(credentials.workspaceId))
            return QwenRemoteResult.Failed("千问 WorkspaceId 配置无效")
        val request = request(credentials)
        val result = CompletableDeferred<QwenRemoteResult>()
        val sentUserText = AtomicBoolean(false)
        val returnedTool = AtomicBoolean(false)
        val outputSent = AtomicBoolean(false)
        val initialResponseDone = AtomicBoolean(false)
        val outputFinished = CompletableDeferred<Unit>()
        val socket = http.newWebSocket(request, object : WebSocketListener() {
            private val userText = text

            override fun onOpen(webSocket: WebSocket, response: Response) {
                VoiceAgentDiagnosticsStore.event("Qwen connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = try { JsonParser.parseString(text).asJsonObject } catch (_: Exception) {
                    if (!result.isCompleted) result.complete(QwenRemoteResult.Failed("千问返回了无法解析的数据"))
                    webSocket.close(1000, "invalid response")
                    return
                }
                when (event.string("type")) {
                    "session.created" -> {
                        VoiceAgentDiagnosticsStore.event("session created")
                        webSocket.send(gson.toJson(QwenAgentProtocol.sessionUpdate(context)))
                    }
                    "session.updated" -> if (sentUserText.compareAndSet(false, true)) {
                        VoiceAgentDiagnosticsStore.event("session updated")
                        webSocket.send(gson.toJson(QwenAgentProtocol.userText(userText)))
                        VoiceAgentDiagnosticsStore.event("user text sent")
                        webSocket.send(gson.toJson(QwenAgentProtocol.responseCreate()))
                    }
                    "response.function_call_arguments.done" -> {
                        if (!returnedTool.compareAndSet(false, true)) return
                        val callId = event.string("call_id")
                        val name = event.string("name")
                        val arguments = event.string("arguments")
                        if (callId == null || name == null || arguments == null) {
                            if (!result.isCompleted) result.complete(QwenRemoteResult.InvalidToolCall("千问工具调用缺少必要字段"))
                            webSocket.close(1000, "invalid tool call")
                            return
                        }
                        VoiceAgentDiagnosticsStore.event("tool name = $name")
                        VoiceAgentDiagnosticsStore.event("tool arguments received")
                        result.complete(QwenRemoteResult.ToolCall(callId, name, arguments, { output ->
                            VoiceAgentDiagnosticsStore.event("function_call_output sent")
                            outputSent.set(true)
                            webSocket.send(gson.toJson(QwenAgentProtocol.functionOutput(callId, output)))
                            webSocket.send(gson.toJson(QwenAgentProtocol.responseCreate()))
                            try { withTimeout(5_000) { outputFinished.await() } }
                            catch (_: TimeoutCancellationException) { VoiceAgentDiagnosticsStore.event("tool output response timeout") }
                            finally { webSocket.close(1000, "completed") }
                        }, { webSocket.cancel() }))
                    }
                    "response.done" -> {
                        if (initialResponseDone.compareAndSet(false, true)) {
                            if (!returnedTool.get() && !result.isCompleted) {
                                result.complete(QwenRemoteResult.NoToolCall)
                                webSocket.close(1000, "no tool call")
                            }
                        } else if (outputSent.get()) {
                            outputFinished.complete(Unit)
                        }
                    }
                    "error" -> if (!result.isCompleted) {
                        VoiceAgentDiagnosticsStore.event("API error")
                        result.complete(QwenRemoteResult.Failed("千问服务返回错误"))
                        webSocket.close(1000, "api error")
                    } else if (outputSent.get()) {
                        VoiceAgentDiagnosticsStore.event("tool output response error")
                        outputFinished.complete(Unit)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                VoiceAgentDiagnosticsStore.event("WebSocket failure = ${t.javaClass.simpleName}")
                if (!result.isCompleted) result.complete(QwenRemoteResult.Failed("无法连接千问服务"))
                outputFinished.complete(Unit)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) result.complete(QwenRemoteResult.Failed("千问连接已关闭"))
                outputFinished.complete(Unit)
            }
        })
        return try {
            withTimeout(timeoutMillis) { result.await() }
        } catch (_: TimeoutCancellationException) {
            VoiceAgentDiagnosticsStore.event("timeout")
            socket.cancel()
            QwenRemoteResult.Failed("千问请求超时")
        } catch (error: CancellationException) {
            socket.cancel()
            throw error
        }
    }

    suspend fun testConnection(credentials: QwenCredentials): QwenConnectionResult {
        if (credentials.apiKey.isBlank() || credentials.workspaceId.isBlank())
            return QwenConnectionResult.Failed("尚未配置")
        if (!Regex("[A-Za-z0-9_-]{1,128}").matches(credentials.workspaceId))
            return QwenConnectionResult.Failed("Workspace ID 格式无效")
        val result = CompletableDeferred<QwenConnectionResult>()
        val socket = http.newWebSocket(request(credentials), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val type = try { JsonParser.parseString(text).asJsonObject.string("type") }
                    catch (_: Exception) { null }
                when (type) {
                    "session.created" -> {
                        result.complete(QwenConnectionResult.Success)
                        webSocket.close(1000, "connection test complete")
                    }
                    "error" -> {
                        result.complete(QwenConnectionResult.Failed("服务拒绝连接"))
                        webSocket.close(1000, "connection test failed")
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val reason = if (response?.code == 401 || response?.code == 403) "认证失败"
                    else "无法连接服务"
                result.complete(QwenConnectionResult.Failed(reason))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (!result.isCompleted) result.complete(QwenConnectionResult.Failed("连接已关闭"))
            }
        })
        return try {
            withTimeout(timeoutMillis) { result.await() }
        } catch (_: TimeoutCancellationException) {
            socket.cancel()
            QwenConnectionResult.Failed("连接超时")
        }
    }

    private fun request(credentials: QwenCredentials): Request {
        val url = "wss://${credentials.workspaceId}.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=${QwenAgentProtocol.MODEL}"
        return Request.Builder().url(url)
            .header("Authorization", "Bearer ${credentials.apiKey}")
            .build()
    }

    private fun com.google.gson.JsonObject.string(name: String): String? = try {
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    } catch (_: Exception) { null }
}

class QwenVoiceAgentEngine(
    private val configuration: QwenConfigProvider,
    private val client: QwenTextClient = QwenRealtimeClient(),
    private val mapper: QwenToolCallMapper = QwenToolCallMapper(VoiceAgentDiagnosticsStore::event),
    private val audioClient: QwenAudioClient = QwenRealtimeAudioClient(),
) : VoiceAgentEngine {
    override suspend fun executeText(text: String, context: VoiceAgentContext): VoiceAgentResult {
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.event("input type = TEXT")
        val config = when (val state = configuration.getConfiguration()) {
            QwenConfigState.NotConfigured -> {
                VoiceAgentDiagnosticsStore.event("AI configured = false")
                VoiceAgentDiagnosticsStore.event("WorkspaceId configured = false")
                VoiceAgentDiagnosticsStore.event("API Key configured = false")
                return VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NotConfigured, "千问尚未配置")
            }
            QwenConfigState.Unavailable -> {
                VoiceAgentDiagnosticsStore.event("AI configuration unavailable")
                return VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService,
                    "无法读取千问配置")
            }
            is QwenConfigState.Configured -> state.credentials
        }
        VoiceAgentDiagnosticsStore.event("AI configured = true")
        VoiceAgentDiagnosticsStore.event("WorkspaceId configured = true")
        VoiceAgentDiagnosticsStore.event("API Key configured = true")
        var remote: QwenRemoteResult = QwenRemoteResult.Failed("无法连接千问服务")
        for (attempt in 0..1) {
            remote = try {
                client.executeText(config, text, context)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                QwenRemoteResult.Failed("无法连接千问服务")
            }
            if (remote !is QwenRemoteResult.Failed) break
            if (attempt == 0) VoiceAgentDiagnosticsStore.event("reconnect")
        }
        return mapRemote(remote, text, context)
    }

    override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext): VoiceAgentResult {
        val config = when (val state = configuration.getConfiguration()) {
            QwenConfigState.NotConfigured -> {
                turn.cancel()
                return VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NotConfigured, "千问尚未配置")
            }
            QwenConfigState.Unavailable -> {
                turn.cancel()
                return VoiceAgentResult.Unavailable(VoiceAgentUnavailableReason.NetworkOrService, "无法读取千问配置")
            }
            is QwenConfigState.Configured -> state.credentials
        }
        val remote = try {
            audioClient.executeAudio(config, turn, context)
        } catch (error: CancellationException) {
            turn.cancel()
            throw error
        } catch (_: Exception) {
            turn.cancel()
            QwenRemoteResult.Failed("千问语音连接失败")
        }
        // Audio has no client-side transcript. The model-provided source_text supplies
        // conservative semantic cues; ambiguous tool choices still enter clarification.
        return mapRemote(remote, "", context)
    }

    private suspend fun mapRemote(remote: QwenRemoteResult, originalText: String,
        context: VoiceAgentContext): VoiceAgentResult = when (val value = remote) {
            QwenRemoteResult.NoToolCall -> {
                VoiceAgentDiagnosticsStore.reason("no_tool_call")
                VoiceAgentResult.NoAction()
            }
            is QwenRemoteResult.InvalidToolCall -> {
                VoiceAgentDiagnosticsStore.reason("invalid_tool_call")
                VoiceAgentResult.InvalidToolCall(value.message)
            }
            is QwenRemoteResult.Failed -> VoiceAgentResult.Unavailable(
                VoiceAgentUnavailableReason.NetworkOrService, value.message)
            is QwenRemoteResult.ToolCall -> try {
                VoiceAgentDiagnosticsStore.tool(value.name)
                VoiceAgentDiagnosticsStore.rawArguments(value.arguments)
                if (originalText.isNotBlank()) VoiceAgentDiagnosticsStore.source(originalText)
                val draft = mapper.map(value.name, value.arguments, originalText, context)
                VoiceAgentDiagnosticsStore.source(draft.originalText)
                VoiceAgentDiagnosticsStore.event("arguments valid = true")
                VoiceAgentDiagnosticsStore.mappedAction(draft.action.name)
                VoiceAgentResult.Action(draft, object : VoiceAgentPendingCall {
                    override val callId = value.callId
                    override val toolName = value.name
                    override val arguments = value.arguments
                    override suspend fun complete(output: VoiceAgentToolOutput) {
                        VoiceAgentDiagnosticsStore.event("execution result = ${output.success}")
                        value.sendOutput(gson.toJson(output))
                    }
                    override fun cancel() = value.cancel()
                })
            } catch (error: InvalidVoiceToolCall) {
                VoiceAgentDiagnosticsStore.event("arguments valid = false")
                VoiceAgentDiagnosticsStore.validationResult("invalid_arguments")
                VoiceAgentDiagnosticsStore.reason(if (error is UnsupportedVoiceToolCall) "unsupported_tool" else "invalid_arguments")
                val unsupported = error is UnsupportedVoiceToolCall
                value.sendOutput(gson.toJson(VoiceAgentToolOutput(false,
                    reason = if (unsupported) "unsupported_tool" else "invalid_arguments")))
                VoiceAgentResult.InvalidToolCall(if (unsupported) "AI 请求了不支持的操作" else "AI 返回的数据格式无效，请重试")
            }
        }

    private val gson = Gson()
}
