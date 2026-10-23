package io.github.peanutpersimmon.rilo.ui

import android.Manifest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.audio.VoiceAudioRecorder
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.voice.*
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenAudioTurnController
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentEngine
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentResult
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentPendingCall
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentToolOutput
import io.github.peanutpersimmon.rilo.presentation.editor.EditorViewModelFactory
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.voice.VoiceViewModel
import io.github.peanutpersimmon.rilo.ui.components.AppSnackbar
import io.github.peanutpersimmon.rilo.ui.feature.voice.VoiceInputSheet
import io.github.peanutpersimmon.rilo.ui.feature.voice.VoiceOverlayHost
import io.github.peanutpersimmon.rilo.ui.feature.voice.observeOutsideVoicePress
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test

class VoiceInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var db: RidoDatabase? = null
    private lateinit var model: VoiceViewModel
    private val keyboard = CountingKeyboard()
    private val request = mutableStateOf(0)
    private val held = mutableStateOf(true)

    @After fun closeDatabase() { db?.close() }

    private fun open(audio: Boolean, initial: String = "", agent: VoiceAgentEngine? = null,
        recorder: VoiceAudioRecorder = FakeRecorder(CompletableDeferred<Unit>())) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (audio) try {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
        } catch (error: SecurityException) {
            assumeNoException("Device shell cannot grant RECORD_AUDIO", error)
        }
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        db = database
        val repo = RoomRidoRepository(database)
        model = VoiceViewModel(repo, agent, recorderFactory = { recorder })
        val factory = EditorViewModelFactory(repo)
        val event = factory.create(EventEditorViewModel::class.java)
        val record = factory.create(SessionEditorViewModel::class.java)
        model.editText(initial)
        compose.setContent {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                RidoTheme {
                    VoiceInputSheet(model, event, record,
                        autoStart = audio, holdActive = held.value, requestId = request.value,
                        onDismiss = {})
                }
            }
        }
    }

    @Test fun textEntryRequestsKeyboardAndRepeatedRequestPreservesText() {
        open(false, "今晚19-20")
        compose.onNodeWithTag("quick_input_text").assertExists().assertIsFocused()
        compose.runOnIdle { assertTrue(keyboard.shows > 0); request.value++ }
        compose.onNodeWithTag("quick_input_text").assertTextEquals("今晚19-20")
    }

    @Test fun repeatedLongPressRequestDoesNotStartSecondRecorder() {
        val completed = CompletableDeferred<Unit>()
        val recorder = FakeRecorder(completed)
        val agent = object : VoiceAgentEngine {
            override suspend fun executeText(text: String, context: VoiceAgentContext) = VoiceAgentResult.NoAction()
            override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext): VoiceAgentResult {
                completed.await()
                return VoiceAgentResult.NoAction()
            }
        }
        open(true, agent = agent, recorder = recorder)
        compose.waitUntil(3_000) { recorder.starts == 1 }
        compose.runOnIdle { request.value++ }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, recorder.starts); held.value = false }
        compose.waitUntil(3_000) { recorder.stops == 1 }
    }

    @Test fun outsideObserverDoesNotConsumeCardClick() {
        var dismissedKeyboard = 0
        var clickedCard = 0
        compose.setContent {
            RidoTheme {
                Column(Modifier.fillMaxSize().observeOutsideVoicePress({ null }, { Offset.Zero }) { dismissedKeyboard++ }) {
                    Text("卡片", Modifier.clickable { clickedCard++ })
                }
            }
        }
        compose.onNodeWithText("卡片").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, dismissedKeyboard); assertEquals(1, clickedCard) }
    }

    @Test fun imeMovesOnlyOverlayAndDoesNotResizePageOrLiftBottomBar() {
        val imeHeight = mutableStateOf(0.dp)
        compose.setContent {
            RidoTheme {
                VoiceOverlayHost(voiceOpen = true, imeInsets = WindowInsets(bottom = imeHeight.value),
                    content = { Box(Modifier.fillMaxSize().testTag("page")) },
                    bottomBar = { Text("底栏", Modifier.testTag("bottom_bar")) },
                    voiceInput = { Text("快速输入", Modifier.testTag("input_overlay")) }, snackbar = {})
            }
        }
        val pageBefore = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val inputBefore = compose.onNodeWithTag("input_overlay").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { imeHeight.value = 240.dp }
        compose.onNodeWithTag("bottom_bar").assertDoesNotExist()
        assertEquals(pageBefore, compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot)
        assertTrue(compose.onNodeWithTag("input_overlay").fetchSemanticsNode().boundsInRoot.top < inputBefore.top)
        compose.runOnIdle { imeHeight.value = 0.dp }
        compose.onNodeWithTag("bottom_bar").assertExists()
        assertEquals(inputBefore, compose.onNodeWithTag("input_overlay").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun compactSnackbarShowsShortMessageAndUndoAction() {
        var undo = 0
        val data = object : SnackbarData {
            override val visuals = object : SnackbarVisuals {
                override val message = "已创建「选修课」"
                override val actionLabel = "撤销"
                override val withDismissAction = false
                override val duration = SnackbarDuration.Long
            }
            override fun performAction() { undo++ }
            override fun dismiss() = Unit
        }
        compose.setContent { RidoTheme { AppSnackbar(data) } }
        compose.onNodeWithTag("compact_snackbar").assertExists()
        compose.onNodeWithText("已创建「选修课」").assertExists()
        compose.onNodeWithText("撤销").performClick()
        compose.runOnIdle { assertEquals(1, undo) }
    }

    @Test fun pastPointRecordExecutesWithoutStorageConfirmation() {
        val agent = object : VoiceAgentEngine {
            override suspend fun executeText(text: String, context: VoiceAgentContext): VoiceAgentResult {
                val local = context.now.minusSeconds(3_600).atZone(context.zone)
                val draft = VoiceActionDraft(VoiceAction.CreateRecord, "买早饭",
                    VoiceFields(title = VoiceFieldPatch.Set("买早饭"),
                        startAt = local.toInstant().toEpochMilli()),
                    text, context.now.toEpochMilli(), semanticIntent = VoiceSemanticIntent.ReportedPoint,
                    recordTemporalType = io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType.POINT)
                return VoiceAgentResult.Action(draft, object : VoiceAgentPendingCall {
                    override val callId = "point"
                    override val toolName = "create_record"
                    override val arguments = "{}"
                    override suspend fun complete(output: VoiceAgentToolOutput) = Unit
                    override fun cancel() = Unit
                })
            }
            override suspend fun executeAudio(turn: QwenAudioTurnController, context: VoiceAgentContext) =
                VoiceAgentResult.NoAction()
        }
        open(false, "早上9点半买早饭了", agent)
        compose.onNodeWithTag("quick_input_execute").performClick()
        compose.waitUntil(5_000) {
            model.uiState.value.text.isEmpty() && !model.uiState.value.busy
        }
        compose.onNodeWithText("保存时间点").assertDoesNotExist()
        compose.onNodeWithText("补充时段").assertDoesNotExist()
        compose.runOnIdle { assertNull(model.uiState.value.clarification) }
    }

    private class CountingKeyboard : SoftwareKeyboardController {
        var shows = 0
        override fun show() { shows++ }
        override fun hide() = Unit
    }
    private class FakeRecorder(private val completed: CompletableDeferred<Unit>) : VoiceAudioRecorder {
        @Volatile var starts = 0
        @Volatile var stops = 0
        override suspend fun start(onChunk: (ByteArray) -> Unit, onFailure: (Throwable) -> Unit) {
            starts++
            onChunk(byteArrayOf(1, 2))
        }
        override suspend fun stop() { stops++; completed.complete(Unit) }
        override fun cancel() = Unit
    }
}
