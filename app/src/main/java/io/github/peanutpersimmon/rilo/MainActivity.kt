package io.github.peanutpersimmon.rilo

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.github.peanutpersimmon.rilo.presentation.AppViewModel
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.DefaultCategoryInitializer
import io.github.peanutpersimmon.rilo.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rilo.domain.usecase.GetClassificationLabelsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveActiveSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.ObserveTimelineDataChangesUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.StopActiveSessionUseCase
import io.github.peanutpersimmon.rilo.presentation.timer.TimerViewModel
import io.github.peanutpersimmon.rilo.presentation.today.TodayViewModel
import io.github.peanutpersimmon.rilo.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rilo.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rilo.domain.usecase.GetStatisticsUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.GetSleepReportUseCase
import io.github.peanutpersimmon.rilo.presentation.settings.SleepReportViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.EditorViewModelFactory
import io.github.peanutpersimmon.rilo.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rilo.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rilo.ui.RidoApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import io.github.peanutpersimmon.rilo.reminder.ReminderAnchor
import io.github.peanutpersimmon.rilo.reminder.ReminderNotifications
import io.github.peanutpersimmon.rilo.reminder.ReminderRuntime
import io.github.peanutpersimmon.rilo.reminder.RidoApplication
import io.github.peanutpersimmon.rilo.ui.navigation.AppRoute
import io.github.peanutpersimmon.rilo.ui.navigation.RootDestination
import io.github.peanutpersimmon.rilo.backup.BackupRestartActivity
import io.github.peanutpersimmon.rilo.backup.BackupRestartAcknowledgementReceiver
import io.github.peanutpersimmon.rilo.domain.time.RuntimeZoneId

class MainActivity : ComponentActivity() {
    private val database by lazy { RidoDatabase.getInstance(applicationContext) }
    private val viewModel: AppViewModel by viewModels()
    private val repository by lazy { RoomRidoRepository(database) }
    private val qwenCredentialStore by lazy {
        io.github.peanutpersimmon.rilo.data.preferences.AndroidQwenCredentialStore(applicationContext)
    }
    private val qwenConfigProvider by lazy {
        io.github.peanutpersimmon.rilo.data.remote.qwen.StoredQwenConfigProvider(qwenCredentialStore)
    }
    private val qwenClient by lazy {
        io.github.peanutpersimmon.rilo.data.remote.qwen.QwenRealtimeClient()
    }
    private val metadataViewModel: io.github.peanutpersimmon.rilo.presentation.settings.MetadataViewModel by viewModels {
        io.github.peanutpersimmon.rilo.presentation.settings.MetadataViewModel.factory(
            io.github.peanutpersimmon.rilo.domain.usecase.CategoryUseCases(
                io.github.peanutpersimmon.rilo.data.repository.RoomCategoryRepository(database)),
            io.github.peanutpersimmon.rilo.domain.usecase.GetRecentLocationsUseCase(
                io.github.peanutpersimmon.rilo.data.repository.RoomLocationHistoryRepository(
                    database)),

        )
    }
    private val voiceViewModel: io.github.peanutpersimmon.rilo.presentation.voice.VoiceViewModel by viewModels {
        io.github.peanutpersimmon.rilo.presentation.voice.VoiceViewModel.factory(
            repository,
            io.github.peanutpersimmon.rilo.data.remote.qwen.QwenVoiceAgentEngine(
                qwenConfigProvider, qwenClient),
        )
    }
    private val qwenSettingsViewModel:
        io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel by viewModels {
        io.github.peanutpersimmon.rilo.presentation.settings.QwenSettingsViewModel.factory(
            qwenCredentialStore, qwenConfigProvider, qwenClient)
    }
    private val editorFactory by lazy { EditorViewModelFactory(repository) }
    private val eventEditorViewModel: EventEditorViewModel by viewModels { editorFactory }
    private val sessionEditorViewModel: SessionEditorViewModel by viewModels { editorFactory }
    private val getLabels by lazy { GetClassificationLabelsUseCase(repository) }
    private val todayViewModel: TodayViewModel by viewModels {
        TodayViewModel.factory(
            observeChanges = ObserveTimelineDataChangesUseCase(repository),
            observeActiveSession = ObserveActiveSessionUseCase(repository),
            getStableSnapshot = io.github.peanutpersimmon.rilo.domain.usecase.GetTodayStableSnapshotUseCase(repository),
        )
    }
    private val planViewOrderPreferences by lazy {
        io.github.peanutpersimmon.rilo.data.preferences.PlanViewOrderPreferences(applicationContext)
    }
    private val planViewModel: PlanViewModel by viewModels {
        PlanViewModel.factory(repository, planViewOrderPreferences)
    }
    private val timerViewModel: TimerViewModel by viewModels {
        TimerViewModel.factory(
            observeActiveSession = ObserveActiveSessionUseCase(repository),
            stopActiveSession = StopActiveSessionUseCase(repository),
            getLabels = getLabels,
        )
    }

    private val statisticsViewModel: StatisticsViewModel by viewModels {
        StatisticsViewModel.factory(ObserveTimelineDataChangesUseCase(repository), GetStatisticsUseCase(repository))
    }
    private val sleepReportViewModel: SleepReportViewModel by viewModels {
        SleepReportViewModel.factory(ObserveTimelineDataChangesUseCase(repository), GetSleepReportUseCase(repository))
    }

    override fun onPause() {
        voiceViewModel.cancelAudio()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        RuntimeZoneId.refresh()
        if (!(application as RidoApplication).recoveryFailed) ReminderRuntime.onForeground(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((application as RidoApplication).recoveryFailed) {
            setContent { Text("数据恢复未完成。请保留应用数据并重新打开 RILO 重试。") }
            return
        }
        enableEdgeToEdge()
        setContent {
            RidoApp(
                viewModel = viewModel,
                voiceViewModel = voiceViewModel,
                metadataViewModel = metadataViewModel,
                qwenSettingsViewModel = { qwenSettingsViewModel },
                todayViewModel = todayViewModel,
                planViewModel = planViewModel,
                statisticsViewModel = statisticsViewModel,
                sleepReportViewModel = { sleepReportViewModel },
                timerViewModel = timerViewModel,
                eventEditorViewModel = eventEditorViewModel,
                sessionEditorViewModel = sessionEditorViewModel,
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DefaultCategoryInitializer(database).initialize()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // The Room transaction rolls back; the new-database marker allows a safe retry.
                Log.e("RidoCategoryInit", "Default category initialization failed", error)
            }
        }
        lifecycleScope.launch {
            openReminderIntent(intent)
            intent.getStringExtra(BackupRestartActivity.EXTRA_TOKEN)?.let { token ->
                window.decorView.post {
                    sendBroadcast(Intent(this@MainActivity, BackupRestartAcknowledgementReceiver::class.java)
                        .putExtra(BackupRestartActivity.EXTRA_TOKEN, token))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { openReminderIntent(intent) }
    }

    private suspend fun openReminderIntent(openIntent: Intent?) {
        val id = openIntent?.getLongExtra(ReminderNotifications.EXTRA_OWNER_ID, 0L) ?: return
        val anchor = openIntent.getStringExtra(ReminderNotifications.EXTRA_ANCHOR)
        openIntent.removeExtra(ReminderNotifications.EXTRA_OWNER_ID)
        openIntent.removeExtra(ReminderNotifications.EXTRA_ANCHOR)
        if (id <= 0) return
        val route = when (anchor) {
            ReminderAnchor.EVENT_START.name ->
                database.eventDao().getById(id)?.let { AppRoute.EventEditor(id) }
            else -> null
        }
        if (route != null) viewModel.navigate(route) else viewModel.selectDestination(RootDestination.Today)
    }
}

