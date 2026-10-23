package io.github.peanutpersimmon.rilo.ui.feature.today

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

private val TodayDateNumberStyle = TextStyle(
    fontSize = 42.sp,
    lineHeight = 42.sp,
    fontWeight = FontWeight.SemiBold,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Top,
        trim = LineHeightStyle.Trim.Both,
    ),
)

private val TodayDateMetaStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.5.sp,
    fontStyle = FontStyle.Normal,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

@Composable
fun TodayHeader(
    dateLabel: String,
    onAdd: () -> Unit,
    onSleep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // PageHeader owns the shared height and page insets. The visible date and action
    // glyphs share a bottom anchor; the two date texts retain typographic baseline alignment.
    Row(
        modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs),
        ) {
            Text(
                dateLabel.substringBefore('\n'),
                Modifier.alignByBaseline(),
                style = TodayDateNumberStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                dateLabel.substringAfter('\n', ""),
                Modifier
                    .weight(1f)
                    .alignByBaseline(),
                style = TodayDateMetaStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeaderAction(
            Icons.Outlined.Bedtime,
            "新增睡眠",
            onSleep,
        )
        HeaderAction(
            Icons.Outlined.Add,
            "新增",
            onAdd,
        )
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    // The touch target stays full-size while the visible glyph participates in the
    // shared bottom-edge contract. No optical offset constant is required.
    Box(
        Modifier
            .width(AppTheme.dimens.minimumTouchTarget)
            .height(AppTheme.dimens.icon + AppTheme.dimens.spaceS)
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            icon,
            description,
            Modifier.padding(bottom = AppTheme.dimens.spaceS)
                .size(AppTheme.dimens.icon),
        )
    }
}
