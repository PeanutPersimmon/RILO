package io.github.peanutpersimmon.rilo.widget.sleep

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import io.github.peanutpersimmon.rilo.R

enum class SleepWidgetLayoutType(val value: String) {
    Horizontal("horizontal"),
    Vertical("vertical"),
}

enum class SleepWidgetActionType(val value: String) {
    Wake("wake"),
    Sleep("sleep"),
}

internal val WidgetLayoutTypeKey = ActionParameters.Key<String>("layout_type")
internal val LastSuccessActionKey = stringPreferencesKey("last_success_action")

/** Fixed values intentionally match the source Sleepc widget. */
private object SleepWidgetTokens {
    val horizontalWidth = 120.dp
    val horizontalHeight = 52.dp
    val horizontalRadius = 26.dp
    val horizontalPadding = 7.dp
    val horizontalVerticalPadding = 6.dp

    val verticalWidth = 56.dp
    val verticalHeight = 120.dp
    val verticalRadius = 28.dp
    val verticalHorizontalPadding = 6.dp
    val verticalPadding = 7.dp

    val actionSize = 40.dp
    val actionRadius = 20.dp
    val iconSize = 25.dp

    val glassSurface = ColorProvider(Color(0x4AFFFFFF))
    val glassHighlight = ColorProvider(Color(0x24FFFFFF))
    val iconColor = ColorProvider(Color(0xE642536A))
}

private data class SleepWidgetContentDescriptions(
    val sleep: String,
    val wake: String,
    val success: String,
)

class SleepCheckWidget(
    private val layoutType: SleepWidgetLayoutType = SleepWidgetLayoutType.Horizontal,
) : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val descriptions = SleepWidgetContentDescriptions(
            sleep = context.getString(R.string.sleep_check_widget_sleep_content_description),
            wake = context.getString(R.string.sleep_check_widget_wake_content_description),
            success = context.getString(R.string.sleep_check_widget_success_content_description),
        )
        provideContent {
            SleepCheckWidgetContent(
                layoutType = layoutType,
                descriptions = descriptions,
            )
        }
    }
}

@Composable
private fun SleepCheckWidgetContent(
    layoutType: SleepWidgetLayoutType,
    descriptions: SleepWidgetContentDescriptions,
) {
    val lastSuccessAction = currentState<Preferences>()[LastSuccessActionKey]

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (layoutType == SleepWidgetLayoutType.Horizontal) {
            Row(
                modifier = GlanceModifier
                    .size(SleepWidgetTokens.horizontalWidth, SleepWidgetTokens.horizontalHeight)
                    .background(SleepWidgetTokens.glassSurface)
                    .cornerRadius(SleepWidgetTokens.horizontalRadius)
                    .padding(
                        horizontal = SleepWidgetTokens.horizontalPadding,
                        vertical = SleepWidgetTokens.horizontalVerticalPadding,
                    ),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                SleepWidgetHorizontalActionSlot(
                    actionType = SleepWidgetActionType.Sleep,
                    layoutType = layoutType,
                    showCheck = lastSuccessAction == SleepWidgetActionType.Sleep.value,
                    descriptions = descriptions,
                )
                SleepWidgetHorizontalActionSlot(
                    actionType = SleepWidgetActionType.Wake,
                    layoutType = layoutType,
                    showCheck = lastSuccessAction == SleepWidgetActionType.Wake.value,
                    descriptions = descriptions,
                )
            }
        } else {
            Column(
                modifier = GlanceModifier
                    .size(SleepWidgetTokens.verticalWidth, SleepWidgetTokens.verticalHeight)
                    .background(SleepWidgetTokens.glassSurface)
                    .cornerRadius(SleepWidgetTokens.verticalRadius)
                    .padding(
                        horizontal = SleepWidgetTokens.verticalHorizontalPadding,
                        vertical = SleepWidgetTokens.verticalPadding,
                    ),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                SleepWidgetVerticalActionSlot(
                    actionType = SleepWidgetActionType.Sleep,
                    layoutType = layoutType,
                    showCheck = lastSuccessAction == SleepWidgetActionType.Sleep.value,
                    descriptions = descriptions,
                )
                SleepWidgetVerticalActionSlot(
                    actionType = SleepWidgetActionType.Wake,
                    layoutType = layoutType,
                    showCheck = lastSuccessAction == SleepWidgetActionType.Wake.value,
                    descriptions = descriptions,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SleepWidgetHorizontalActionSlot(
    actionType: SleepWidgetActionType,
    layoutType: SleepWidgetLayoutType,
    showCheck: Boolean,
    descriptions: SleepWidgetContentDescriptions,
) {
    Box(
        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        SleepWidgetIconButton(
            actionType = actionType,
            layoutType = layoutType,
            showCheck = showCheck,
            descriptions = descriptions,
        )
    }
}

@Composable
private fun ColumnScope.SleepWidgetVerticalActionSlot(
    actionType: SleepWidgetActionType,
    layoutType: SleepWidgetLayoutType,
    showCheck: Boolean,
    descriptions: SleepWidgetContentDescriptions,
) {
    Box(
        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SleepWidgetIconButton(
            actionType = actionType,
            layoutType = layoutType,
            showCheck = showCheck,
            descriptions = descriptions,
        )
    }
}

@Composable
private fun SleepWidgetIconButton(
    actionType: SleepWidgetActionType,
    layoutType: SleepWidgetLayoutType,
    showCheck: Boolean,
    descriptions: SleepWidgetContentDescriptions,
) {
    val action = when (actionType) {
        SleepWidgetActionType.Wake -> actionRunCallback<WakeCheckInAction>(
            actionParametersOf(WidgetLayoutTypeKey to layoutType.value),
        )
        SleepWidgetActionType.Sleep -> actionRunCallback<SleepCheckInAction>(
            actionParametersOf(WidgetLayoutTypeKey to layoutType.value),
        )
    }
    val iconRes = if (showCheck) {
        R.drawable.ic_widget_check
    } else {
        when (actionType) {
            SleepWidgetActionType.Wake -> R.drawable.ic_widget_wake_sun
            SleepWidgetActionType.Sleep -> R.drawable.ic_widget_sleep_moon
        }
    }
    val contentDescription = if (showCheck) {
        descriptions.success
    } else {
        when (actionType) {
            SleepWidgetActionType.Wake -> descriptions.wake
            SleepWidgetActionType.Sleep -> descriptions.sleep
        }
    }

    Box(
        modifier = GlanceModifier
            .size(SleepWidgetTokens.actionSize)
            .background(SleepWidgetTokens.glassHighlight)
            .cornerRadius(SleepWidgetTokens.actionRadius)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(SleepWidgetTokens.iconSize),
            colorFilter = ColorFilter.tint(SleepWidgetTokens.iconColor),
        )
    }
}
