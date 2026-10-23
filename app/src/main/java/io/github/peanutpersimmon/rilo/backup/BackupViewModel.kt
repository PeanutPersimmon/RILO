package io.github.peanutpersimmon.rilo.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

enum class BackupUiPhase { IDLE, EXPORTING, VALIDATING, AWAIT_FIRST_CONFIRM, AWAIT_TYPED_CONFIRM,
    SAVING_CURRENT, RESTORING, RESTART_REQUIRED, RESTART_LAUNCHING }

data class BackupUiState(val phase: BackupUiPhase = BackupUiPhase.IDLE, val message: String? = null)

internal fun RestoreOutcome.restartUiState(): BackupUiState = when (this) {
    RestoreOutcome.SUCCESS_RESTART -> BackupUiState(BackupUiPhase.RESTART_REQUIRED, "导入成功")
    RestoreOutcome.ROLLED_BACK_RESTART -> BackupUiState(BackupUiPhase.RESTART_REQUIRED,
        "导入失败，已恢复原数据")
    RestoreOutcome.RECOVERY_PENDING_RESTART -> BackupUiState(BackupUiPhase.RESTART_REQUIRED,
        "导入中断，重新启动后将尝试恢复原数据")
}

/** Separates a completed restore from the user's explicit process restart action. */
internal class BackupRestartFlow(private val launch: () -> Unit) {
    private var required = false

    fun onRestore(outcome: RestoreOutcome): BackupUiState {
        required = true
        return outcome.restartUiState()
    }

    fun launchIfRequired(phase: BackupUiPhase): Boolean {
        if (!required || phase != BackupUiPhase.RESTART_REQUIRED) return false
        launch()
        return true
    }
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val service = BackupService(application)
    private val restartFlow = BackupRestartFlow { BackupRestartActivity.restart(getApplication()) }
    private val mutableState = MutableStateFlow(BackupUiState())
    val state = mutableState.asStateFlow()
    private var prepared: PreparedBackup? = null

    fun suggestedFileName(): String = service.suggestedFileName()

    fun export(uri: Uri) {
        if (mutableState.value.phase != BackupUiPhase.IDLE) return
        mutableState.value = BackupUiState(BackupUiPhase.EXPORTING, "正在导出…")
        viewModelScope.launch {
            try {
                service.export(uri)
                val success = BackupUiState(message = "导出成功。备份包含个人数据，请妥善保管。")
                mutableState.value = success
                delay(1_000L)
                if (mutableState.value == success) mutableState.value = BackupUiState()
            } catch (error: Exception) {
                mutableState.value = BackupUiState(message = "导出失败，请检查保存位置与可用空间")
            }
        }
    }

    fun prepare(uri: Uri) {
        if (mutableState.value.phase != BackupUiPhase.IDLE) return
        mutableState.value = BackupUiState(BackupUiPhase.VALIDATING, "正在校验…")
        viewModelScope.launch {
            try {
                prepared = service.prepareImport(uri)
                mutableState.value = BackupUiState(BackupUiPhase.AWAIT_FIRST_CONFIRM)
            } catch (error: Exception) {
                mutableState.value = BackupUiState(message =
                    (error as? InvalidBackupException)?.userMessage ?: "备份文件已损坏")
            }
        }
    }

    fun continueImport() {
        if (mutableState.value.phase == BackupUiPhase.AWAIT_FIRST_CONFIRM && prepared != null)
            mutableState.value = BackupUiState(BackupUiPhase.AWAIT_TYPED_CONFIRM)
    }

    fun cancelImport() {
        if (mutableState.value.phase !in setOf(BackupUiPhase.AWAIT_FIRST_CONFIRM,
                BackupUiPhase.AWAIT_TYPED_CONFIRM)) return
        prepared?.discard()
        prepared = null
        mutableState.value = BackupUiState()
    }

    fun confirmImport(typed: String) {
        if (typed != "确认导入" || mutableState.value.phase != BackupUiPhase.AWAIT_TYPED_CONFIRM) return
        val ready = prepared ?: return
        prepared = null
        mutableState.value = BackupUiState(BackupUiPhase.SAVING_CURRENT, "正在备份当前数据…")
        importScope.launch {
            var requiresRestart = false
            try {
                val outcome = service.restore(ready) { stage ->
                    mutableState.value = when (stage) {
                        BackupStage.VALIDATING -> BackupUiState(BackupUiPhase.VALIDATING, "正在校验…")
                        BackupStage.SAVING_CURRENT -> BackupUiState(BackupUiPhase.SAVING_CURRENT, "正在备份当前数据…")
                        BackupStage.RESTORING -> BackupUiState(BackupUiPhase.RESTORING, "正在恢复…")
                        BackupStage.RESTARTING -> BackupUiState(BackupUiPhase.RESTORING, "正在完成导入…")
                    }
                }
                requiresRestart = true
                mutableState.value = restartFlow.onRestore(outcome)
            } catch (error: Exception) {
                mutableState.value = if (requiresRestart) restartFlow
                    .onRestore(RestoreOutcome.RECOVERY_PENDING_RESTART)
                    .copy(message = "数据已写入，请重新启动 RILO。")
                else BackupUiState(message =
                    (error as? InvalidBackupException)?.userMessage ?: "导入失败，当前数据未改变")
            }
        }
    }

    fun restartNow() {
        if (mutableState.value.phase != BackupUiPhase.RESTART_REQUIRED) return
        val previous = mutableState.value
        mutableState.value = BackupUiState(BackupUiPhase.RESTART_LAUNCHING, "正在重新启动 RILO…")
        try {
            restartFlow.launchIfRequired(previous.phase)
        } catch (error: Exception) {
            mutableState.value = previous.copy(message = "无法自动重新打开 RILO，请再次点击重新启动。")
        }
    }

    override fun onCleared() {
        prepared?.discard()
        super.onCleared()
    }

    companion object {
        // Import closes the process-wide Room singleton. Navigation must not cancel it mid-replace.
        private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
