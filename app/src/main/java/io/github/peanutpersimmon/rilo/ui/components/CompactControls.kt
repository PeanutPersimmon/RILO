package io.github.peanutpersimmon.rilo.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.ui.theme.AppTheme

enum class SegmentedControlLevel { PRIMARY, SECONDARY }

/** Shared level-one navigation geometry for Plan and Statistics.
 *
 *  The entries split the full row width equally. Use [ScrollablePrimaryNavigation]
 *  when labels or a user-defined item count cannot fit without truncation. */
@Composable
fun <T> CompactPrimaryNavigation(
    entries: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val d = AppTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(d.navigationPrimaryHeight),
        horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
    ) {
        entries.forEach { entry ->
            CompactCapsule(
                label(entry),
                entry == selected,
                { onSelect(entry) },
                Modifier.weight(1f),
                level = SegmentedControlLevel.PRIMARY,
            )
        }
    }
}

/** Content-width level-one navigation for a variable number of long labels. */
@Composable
fun <T> ScrollablePrimaryNavigation(
    entries: List<T>,
    selected: T,
    key: (T) -> Any,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val d = AppTheme.dimens
    val listState = rememberLazyListState()
    val selectedIndex = entries.indexOf(selected)

    LaunchedEffect(entries, selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(d.navigationPrimaryHeight),
        horizontalArrangement = Arrangement.spacedBy(d.spaceXs),
    ) {
        items(entries, key = key) { entry ->
            CompactCapsule(
                label = label(entry),
                selected = entry == selected,
                onClick = { onSelect(entry) },
                modifier = Modifier.widthIn(min = d.navigationItemMinWidth),
                level = SegmentedControlLevel.PRIMARY,
            )
        }
    }
}

@Composable
fun CompactCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: SegmentedControlLevel = SegmentedControlLevel.PRIMARY,
) {
    val d = AppTheme.dimens
    val style = when (level) {
        SegmentedControlLevel.PRIMARY -> AppTheme.type.navigationPrimary
        SegmentedControlLevel.SECONDARY -> AppTheme.type.navigationSecondary
    }
    val height = when (level) {
        SegmentedControlLevel.PRIMARY -> d.navigationPrimaryHeight
        SegmentedControlLevel.SECONDARY -> d.navigationSecondaryHeight
    }
    Box(
        modifier
            .height(height)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                AppTheme.shapes.pill,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = d.segmentedHorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = style,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CompactDateNavigation(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLabelClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = AppTheme.type.periodTitle,
    touchTarget: Dp = DATE_NAVIGATION_TOUCH_TARGET,
) {
    val d = AppTheme.dimens
    Row(
        modifier.fillMaxWidth().height(d.dateNavigationHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateNavigationAction(
            previous = true,
            onClick = onPrevious,
            touchTarget = touchTarget,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onLabelClick)
                .testTag("date-navigation-label")
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DateNavigationAction(
            previous = false,
            onClick = onNext,
            touchTarget = touchTarget,
        )
    }
}

internal val DATE_NAVIGATION_TOUCH_TARGET = 44.dp

@Composable
private fun DateNavigationAction(
    previous: Boolean,
    onClick: () -> Unit,
    touchTarget: Dp,
) {
    val d = AppTheme.dimens
    Box(
        Modifier
            .requiredSize(touchTarget)
            .clickable(onClick = onClick)
            .testTag(if (previous) "date-navigation-previous" else "date-navigation-next"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (previous) Icons.AutoMirrored.Outlined.KeyboardArrowLeft
            else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            if (previous) "上一时间范围" else "下一时间范围",
            Modifier.size(d.icon),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun CompactSectionSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = AppTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(AppTheme.dimens.compactCardPadding), content = content)
    }
}

/** The single shared first-row container for every page. It owns one minimum height
 *  ([AppDimens.headerHeight]) so the home date, the settings title and the plan /
 *  statistics level-one tabs all start at the same Y while enlarged text can expand it.
 *  It also owns the root-page horizontal and bottom insets, so callers cannot drift. */
@Composable
fun PageHeader(
    modifier: Modifier = Modifier,
    height: Dp = AppTheme.dimens.compactHeaderHeight,   // 默认给非首页
    contentTopOffset: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val d = AppTheme.dimens
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(height)
            .padding(
                start = d.pageHorizontal,
                end = d.pageHorizontal,
                bottom = d.headerBottomPadding,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val shift = contentTopOffset.roundToPx()
        val newHeight = (placeable.height + shift).coerceAtLeast(0)
        layout(placeable.width, newHeight) {
            placeable.place(0, shift)
        }
    }
) { content() }
    }
}

/** The fixed-height secondary navigation band shared by Plan and Statistics.
 *
 *  It always reserves a level-two slot ([AppDimens.navigationSecondaryHeight]) plus the
 *  date row, so a page that has no level-two tabs (Plan) still occupies exactly the same
 *  vertical space as one that does (Statistics). Keeping the band height constant is what
 *  stops the top of the page from jumping when switching between the two. */
@Composable
fun PageNavBand(
    modifier: Modifier = Modifier,
    secondary: (@Composable () -> Unit)? = null,
    dateNavigation: @Composable () -> Unit,
) {
    val d = AppTheme.dimens
    Column(
        modifier
            .fillMaxWidth()
            .height(d.navBandHeight),
    ) {
        if (secondary != null) {
            secondary()
        } else {
            Spacer(Modifier.height(d.navigationSecondaryHeight))
        }
        Spacer(Modifier.height(d.spaceS))
        dateNavigation()
    }
}
