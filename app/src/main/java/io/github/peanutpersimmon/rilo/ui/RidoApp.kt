package io.github.peanutpersimmon.rilo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rilo.R
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.presentation.AppViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.timer.TimerViewModel
import io.github.peanutpersimmon.rilo.presentation.timer.TimerUiState
import io.github.peanutpersimmon.rilo.presentation.today.TodayViewModel
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportViewModel
import io.github.peanutpersimmon.rilo.ui.feature.statistics.StatisticsScreen
import io.github.peanutpersimmon.rilo.ui.components.AppNavigationBar
import io.github.peanutpersimmon.rilo.ui.components.AppNavigationItem
import io.github.peanutpersimmon.rilo.ui.components.AppScaffold
import io.github.peanutpersimmon.rilo.ui.components.AppSnackbar
import io.github.peanutpersimmon.rilo.ui.components.LocalBottomSheetVisibility
import io.github.peanutpersimmon.rilo.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rilo.ui.feature.editor.event.EventEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.editor.session.SessionEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.timer.TimerScreen
import io.github.peanutpersimmon.rilo.ui.feature.timer.ActiveSessionPill
import io.github.peanutpersimmon.rilo.ui.feature.timer.TimerControlSheet
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayScreen
import io.github.peanutpersimmon.rilo.ui.feature.plan.PlanScreen
import io.github.peanutpersimmon.rilo.ui.feature.today.InlineCreateKind
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayEditorSheet
import io.github.peanutpersimmon.rilo.ui.feature.today.TodayEditorRequest
import io.github.peanutpersimmon.rilo.ui.feature.today.todayEditorRequestFor
import io.github.peanutpersimmon.rilo.ui.navigation.AppRoute
import io.github.peanutpersimmon.rilo.ui.navigation.RootDestination
import io.github.peanutpersimmon.rilo.ui.screen.PlaceholderScreen
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.peanutpersimmon.rilo.ui.feature.voice.VoiceOverlayHost
import androidx.activity.compose.ReportDrawnAfter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RidoApp(
    viewModel: AppViewModel,
    voiceViewModel: io.github.peanutpersimmon.rilo.presentation.voice.VoiceViewModel,
    metadataViewModel: io.github.peanutpersimmon.rilo.presentation.settings.MetadataViewModel,
    qwenSettingsViewModel: () -> io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel,
    todayViewModel: TodayViewModel,
    planViewModel: PlanViewModel,
    statisticsViewModel: StatisticsViewModel,
    sleepReportViewModel: () -> SleepReportViewModel,
    timerViewModel: TimerViewModel,
    eventEditorViewModel: EventEditorViewModel,
    sessionEditorViewModel: SessionEditorViewModel,
) {
    ReportDrawnAfter {
        todayViewModel.uiState.first {
            it is io.github.peanutpersimmon.rilo.presentation.today.TodayUiState.Content
        }
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metadata by metadataViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceOpen by remember { mutableStateOf(false) }
    var voiceAutoStart by remember { mutableStateOf(false) }
    var voiceHoldActive by remember { mutableStateOf(false) }
    var voicePressCancelled by remember { mutableStateOf(false) }
    var voiceStartedImmediately by remember { mutableStateOf(false) }
    var voiceRequest by remember { mutableStateOf(0) }
    var visibleBottomSheets by remember { mutableIntStateOf(0) }
    val bottomSheetVisibilityObserver = remember {
        { visible: Boolean ->
            visibleBottomSheets = (visibleBottomSheets + if (visible) 1 else -1).coerceAtLeast(0)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val imeVisible = rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.executed.collect { receipt ->
            focus.clearFocus(); keyboard?.hide()
            voiceOpen = false
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                snapshotFlow { !imeVisible.value && ime.getBottom(density) == 0 }.first { it }
                if (snackbar.showSnackbar(receipt.message, "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    voiceViewModel.undo(receipt)?.let { snackbar.showSnackbar(it) }
                }
            }
        }
    }
    var todayEditor by remember { mutableStateOf<TodayEditorRequest?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(
        io.github.peanutpersimmon.rilo.ui.feature.settings.LocalMetadataViewModel provides metadataViewModel,
        io.github.peanutpersimmon.rilo.ui.feature.settings.LocalMetadataState provides metadata,
        LocalBottomSheetVisibility provides bottomSheetVisibilityObserver,
    ) {
        RidoTheme(themeMode = uiState.themeMode) {
            // The underlying page stays composed while any editor sheet is visible.
            if (uiState.route == AppRoute.Timer || uiState.route is AppRoute.SessionEditor) {
                TimerScreen(
                    viewModel = timerViewModel,
                    onBack = viewModel::back,
                    onEdit = { id -> viewModel.navigate(AppRoute.SessionEditor(id)) },
                )
            } else {
                RootContent(viewModel, todayViewModel, planViewModel, statisticsViewModel, sleepReportViewModel, timerViewModel,
                    qwenSettingsViewModel, snackbar, voiceOpen,
                    activeOverlaySuppressed = voiceOpen || todayEditor != null ||
                        uiState.route != null || visibleBottomSheets > 0,
                    onVoice = { start ->
                        if (!voiceViewModel.uiState.value.busy && !voiceViewModel.isAudioActive()) {
                            if (!voiceOpen) voiceViewModel.reset()
                            voiceAutoStart = start
                            voiceHoldActive = start
                            voicePressCancelled = false
                            voiceRequest++
                            voiceOpen = true
                            voiceStartedImmediately = start &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (voiceStartedImmediately) voiceViewModel.startAudio()
                        }
                    },
                    onVoiceRelease = { voiceHoldActive = false; voiceViewModel.releaseAudio() },
                    onVoiceCancel = {
                        voiceHoldActive = false
                        voicePressCancelled = true
                        voiceViewModel.cancelAudio()
                        voiceOpen = false
                    },
                    voiceInput = {
                        if (voiceOpen) io.github.peanutpersimmon.rilo.ui.feature.voice.VoiceInputSheet(
                            voiceViewModel, eventEditorViewModel, sessionEditorViewModel,
                            autoStart = voiceAutoStart, holdActive = voiceHoldActive,
                            pressCancelled = voicePressCancelled,
                            startedImmediately = voiceStartedImmediately, requestId = voiceRequest,
                            onDismiss = { voiceHoldActive = false; voiceViewModel.cancelAudio(); voiceOpen = false; voiceViewModel.reset() })
                    },
                    onCreate = { todayEditor = TodayEditorRequest(it) },
                    onEdit = { todayEditor = it },
                )
            }
            when (val route = uiState.route) {
                AppRoute.Timer -> Unit
                is AppRoute.EventEditor -> EventEditorSheet(eventEditorViewModel, route.id, viewModel::back,
                    onCreateRecord = { id -> viewModel.back(); todayEditor = TodayEditorRequest(InlineCreateKind.SESSION, sourceEventId = id) },
                    onChanged = todayViewModel::refreshAfterEditorChange) { snapshot, restore ->
                    viewModel.back()
                    scope.launch { showUndo(snackbar, snapshot, restore) }
                }
                is AppRoute.SessionEditor -> SessionEditorSheet(
                    sessionEditorViewModel, route.id, viewModel::back,
                    onStarted = viewModel::back,
                    onDeleted = { snapshot, restore -> viewModel.back(); scope.launch { showUndo(snackbar, snapshot, restore) } },
                )
                null -> {
                    todayEditor?.let { request ->
                        TodayEditorSheet(
                            request = request,
                            eventViewModel = eventEditorViewModel,
                            sessionViewModel = sessionEditorViewModel,
                            snackbar = snackbar,
                            scope = scope,
                            onDismiss = { todayEditor = null },
                            onEventRecord = { id -> todayEditor = TodayEditorRequest(InlineCreateKind.SESSION, sourceEventId = id) },
                            onEventChanged = todayViewModel::refreshAfterEditorChange,
                            onSessionStarted = { todayEditor = null },
                        )
                    }
                }
            }
        }
    }
}

internal suspend fun <T> showUndo(host: SnackbarHostState, snapshot: T, restore: suspend (T) -> Boolean) {
    val result = host.showSnackbar("已删除", "撤销", duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed && !restore(snapshot)) host.showSnackbar("无法恢复记录")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RootContent(
    viewModel: AppViewModel,
    todayViewModel: TodayViewModel,
    planViewModel: PlanViewModel,
    statisticsViewModel: StatisticsViewModel,
    sleepReportViewModel: () -> SleepReportViewModel,
    timerViewModel: TimerViewModel,
    qwenSettingsViewModel: () -> io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel,
    snackbar: SnackbarHostState,
    voiceOpen: Boolean,
    activeOverlaySuppressed: Boolean,
    onVoice: (Boolean) -> Unit,
    onVoiceRelease: () -> Unit,
    onVoiceCancel: () -> Unit,
    voiceInput: @Composable () -> Unit,
    onCreate: (InlineCreateKind) -> Unit,
    onEdit: (TodayEditorRequest) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerUiState by timerViewModel.uiState.collectAsStateWithLifecycle()
    var timerControlOpen by remember { mutableStateOf(false) }
    val activeSession = timerUiState as? TimerUiState.Active
    LaunchedEffect(activeSession?.sessionId) {
        if (activeSession == null) timerControlOpen = false
    }
    val destinations = RootDestination.entries
    val navigationItems = destinations.map { AppNavigationItem(stringResource(it.labelRes), it.icon) }
    val pageStateHolder = rememberSaveableStateHolder()
    val isTodayVisible = uiState.destination == RootDestination.Today
    LaunchedEffect(isTodayVisible) {
        todayViewModel.setPageVisible(isTodayVisible)
    }
    val isPlanVisible = uiState.destination == RootDestination.Plan
    LaunchedEffect(isPlanVisible) {
        planViewModel.setPageVisible(isPlanVisible)
    }
    val isStatisticsVisible = uiState.destination == RootDestination.Statistics
    LaunchedEffect(isStatisticsVisible) {
        statisticsViewModel.setPageVisible(isStatisticsVisible)
    }
    // safeDrawing includes IME. The root and its nested page scaffold deliberately use only system bars/cutouts.
    val pageInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    VoiceOverlayHost(voiceOpen = voiceOpen,
        bottomBar = {
            AppNavigationBar(items = navigationItems, selectedIndex = destinations.indexOf(uiState.destination),
                onItemSelected = { index ->
                    val next = destinations[index]
                    if (next != uiState.destination) {
                        // Commit date reset before the destination first composes.
                        planViewModel.setPageVisible(next == RootDestination.Plan)
                        statisticsViewModel.setPageVisible(next == RootDestination.Statistics)
                    }
                    viewModel.selectDestination(next)
                },
                onVoiceClick = { onVoice(false) }, onVoiceLongClick = { onVoice(true) },
                onVoiceRelease = onVoiceRelease, onVoiceCancel = onVoiceCancel)
        }, voiceInput = voiceInput,
        snackbar = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        floatingOverlayVisible = activeSession != null && !activeOverlaySuppressed && !timerControlOpen,
        floatingOverlay = {
            activeSession?.let { active ->
                ActiveSessionPill(state = active, onClick = {
                    if (active.kind == SessionKind.ACTIVITY) {
                        timerControlOpen = true
                    } else {
                        val warning = if (active.elapsed.elapsedMillis > 24L * 60L * 60L * 1_000L) {
                            "这条睡眠记录已超过 24 小时，请确认实际起床时间"
                        } else null
                        onEdit(TodayEditorRequest(
                            kind = InlineCreateKind.SESSION,
                            id = active.sessionId,
                            isActive = true,
                            sleep = true,
                            sleepWarning = warning,
                        ))
                    }
                })
            }
        },
        content = {
    AppScaffold(contentWindowInsets = pageInsets) { outerPadding ->
        val destination = uiState.destination
        pageStateHolder.SaveableStateProvider(destination.name) {
        AppScaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.fillMaxSize().padding(outerPadding).consumeWindowInsets(outerPadding),
        topBar = {
            when (destination) {
                RootDestination.Today -> Unit
                RootDestination.Plan -> Unit
                RootDestination.Statistics -> Unit
                RootDestination.Settings -> Unit
            }
        },
    ) { contentPadding ->
        if (destination == RootDestination.Today || destination == RootDestination.Plan) {
            MainContentViewport {
                if (destination == RootDestination.Today) {
                    TodayScreen(
                        todayViewModel, contentPadding,
                        onAddEvent = { onCreate(InlineCreateKind.EVENT) },
                        onAddSession = { onCreate(InlineCreateKind.SESSION) },
                        onAddSleep = { onEdit(TodayEditorRequest(InlineCreateKind.SESSION, sleep = true)) },
                        onPlanItemClick = { item -> onEdit(TodayEditorRequest(InlineCreateKind.EVENT, item.sourceId)) },
                        onEntryClick = { entry ->
                            todayEditorRequestFor(entry)?.let(onEdit)
                        },
                    )
                } else {
                    PlanScreen(planViewModel, contentPadding,
                        onEventClick = { onEdit(TodayEditorRequest(InlineCreateKind.EVENT, it)) },
                        onRecordClick = { id, kind ->
                            onEdit(TodayEditorRequest(
                                InlineCreateKind.SESSION,
                                id,
                                sleep = opensSleepEditor(kind),
                            ))
                        })
                }
            }
        } else if (destination == RootDestination.Statistics) {
            StatisticsScreen(statisticsViewModel, contentPadding, uiState.destination == RootDestination.Statistics)
        } else if (destination == RootDestination.Settings) {
            io.github.peanutpersimmon.rilo.ui.feature.settings.SettingsContent(
                uiState.themeMode, viewModel::setNightMode, qwenSettingsViewModel, planViewModel, sleepReportViewModel,
                Modifier.padding(contentPadding))
        } else {
            val body = when (destination) {
                RootDestination.Plan -> R.string.skeleton_plan_body
                RootDestination.Statistics -> R.string.skeleton_statistics_body
                RootDestination.Settings -> R.string.skeleton_settings_body
                RootDestination.Today -> error("handled above")
            }
            PlaceholderScreen(stringResource(body), Modifier.padding(contentPadding))
        }
        }
    }
    }
    })
    val activity = activeSession?.takeIf { it.kind == SessionKind.ACTIVITY }
    if (timerControlOpen && activity != null) {
        TimerControlSheet(
            state = activity,
            onDismiss = { timerControlOpen = false; timerViewModel.clearError() },
            onEdit = { id ->
                timerControlOpen = false
                onEdit(TodayEditorRequest(InlineCreateKind.SESSION, id, isActive = true))
            },
            onStop = timerViewModel::stop,
        )
    }
}

internal fun opensSleepEditor(kind: SessionKind): Boolean = kind != SessionKind.ACTIVITY

@Composable
private fun MainContentViewport(content: @Composable () -> Unit) {
    // The outer Scaffold consumes system bars; this reserves only the floating app navigation.
    Box(Modifier.fillMaxSize()
        .padding(bottom = LocalFloatingNavigationClearance.current)
        .clipToBounds()) {
        content()
    }
}

