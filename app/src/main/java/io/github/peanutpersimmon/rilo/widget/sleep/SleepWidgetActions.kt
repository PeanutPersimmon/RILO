package io.github.peanutpersimmon.rilo.widget.sleep

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.peanutpersimmon.rilo.domain.model.DomainResult
import kotlinx.coroutines.delay

class SleepCheckInAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val result = SleepWidgetGateway(context.applicationContext).sleepCheckIn()
        handleResult(
            context = context,
            glanceId = glanceId,
            parameters = parameters,
            actionType = SleepWidgetActionType.Sleep,
            result = result,
        )
    }
}

class WakeCheckInAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val result = SleepWidgetGateway(context.applicationContext).wakeCheckIn()
        handleResult(
            context = context,
            glanceId = glanceId,
            parameters = parameters,
            actionType = SleepWidgetActionType.Wake,
            result = result,
        )
    }
}

private suspend fun handleResult(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
    actionType: SleepWidgetActionType,
    result: DomainResult<*>,
) {
    when (result) {
        is DomainResult.Success -> showSuccessCheck(context, glanceId, parameters, actionType)
        is DomainResult.Failure -> {
            clearSuccessAction(context, glanceId)
            updateWidget(context, glanceId, parameters)
        }
    }
}

private suspend fun showSuccessCheck(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
    actionType: SleepWidgetActionType,
) {
    updateSuccessAction(context, glanceId, actionType)
    updateWidget(context, glanceId, parameters)
    delay(SUCCESS_FEEDBACK_MILLIS)
    clearSuccessAction(context, glanceId)
    updateWidget(context, glanceId, parameters)
}

private suspend fun updateSuccessAction(
    context: Context,
    glanceId: GlanceId,
    actionType: SleepWidgetActionType,
) {
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences[LastSuccessActionKey] = actionType.value
    }
}

private suspend fun clearSuccessAction(
    context: Context,
    glanceId: GlanceId,
) {
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences.remove(LastSuccessActionKey)
    }
}

private suspend fun updateWidget(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
) {
    val layoutType = when (parameters[WidgetLayoutTypeKey]) {
        SleepWidgetLayoutType.Vertical.value -> SleepWidgetLayoutType.Vertical
        else -> SleepWidgetLayoutType.Horizontal
    }
    SleepCheckWidget(layoutType).update(context, glanceId)
}

private const val SUCCESS_FEEDBACK_MILLIS = 800L
