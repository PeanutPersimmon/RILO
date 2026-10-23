package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppDimens(
    val spaceMicro: androidx.compose.ui.unit.Dp = 1.dp,
    val spaceXs: androidx.compose.ui.unit.Dp = 4.dp,
    val spaceS: androidx.compose.ui.unit.Dp = 8.dp,
    val spaceM: androidx.compose.ui.unit.Dp = 16.dp,
    val spaceL: androidx.compose.ui.unit.Dp = 24.dp,
    val spaceXl: androidx.compose.ui.unit.Dp = 32.dp,
    val pageHorizontal: androidx.compose.ui.unit.Dp = 16.dp,
    /** Shared top inset for the first row on every root page. */
    val pageHeaderTop: androidx.compose.ui.unit.Dp = spaceMicro,
    /** Shared optical bottom inset inside the first-row container. */
    val headerBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    val topBarHeight: androidx.compose.ui.unit.Dp = 64.dp,
    /** Single shared height for every page's first row (home date, settings title,
     *  plan/statistics level-one tabs) so switching pages never shifts the header. */
    val headerHeight: androidx.compose.ui.unit.Dp = 56.dp,
    val navigationBarHeight: androidx.compose.ui.unit.Dp = 72.dp,
    val minimumTouchTarget: androidx.compose.ui.unit.Dp = 48.dp,
    val icon: androidx.compose.ui.unit.Dp = 24.dp,
    val border: androidx.compose.ui.unit.Dp = 1.dp,
    val dialogMaxWidth: androidx.compose.ui.unit.Dp = 560.dp,
    val sheetHandleWidth: androidx.compose.ui.unit.Dp = 32.dp,
    val sheetHandleHeight: androidx.compose.ui.unit.Dp = 4.dp,
    val timelineTimeColumnWidth: androidx.compose.ui.unit.Dp = 48.dp,
    val timelineAxisLabelWidth: androidx.compose.ui.unit.Dp = 44.dp,
    /** Protects the complete time label plus one standard spacing unit on either side. */
    val timelineAxisGutter: androidx.compose.ui.unit.Dp = timelineAxisLabelWidth / 2 + spaceS,
    val timelineReminderHeight: androidx.compose.ui.unit.Dp = 40.dp,
    val reminderToTimelineGap: androidx.compose.ui.unit.Dp = 8.dp,
    val editorLabelWidth: androidx.compose.ui.unit.Dp = 64.dp,
    val editorRowHeight: androidx.compose.ui.unit.Dp = 48.dp,
    val editorTimeRowGap: androidx.compose.ui.unit.Dp = 0.dp,
    val sheetHandlePadding: androidx.compose.ui.unit.Dp = 8.dp,
    val sheetHeaderContentGap: androidx.compose.ui.unit.Dp = 4.dp,
    val sheetRowVisualHeight: androidx.compose.ui.unit.Dp = 40.dp,
    val editorInputMinHeight: androidx.compose.ui.unit.Dp = 32.dp,
    val editorIconSize: androidx.compose.ui.unit.Dp = 24.dp,
    val editorTimeIconSize: androidx.compose.ui.unit.Dp = 22.dp,
    val editorToolMaxWidth: androidx.compose.ui.unit.Dp = 200.dp,
    val timelineDotSize: androidx.compose.ui.unit.Dp = 5.dp,
    val timelineCardHeight: androidx.compose.ui.unit.Dp = 64.dp,
    val timelineDetailCardHeight: androidx.compose.ui.unit.Dp = 104.dp,
    /** Extra scroll tail used only when dense timeline content extends below the visual axis. */
    val timelineDenseContentTailSpace: androidx.compose.ui.unit.Dp = spaceS,
    /** Small separation retained when sequential timeline cards collide. */
    val timelineItemGap: androidx.compose.ui.unit.Dp = spaceXs,
    /** Width of the legacy compact axis track; no longer used to size event lanes. */
    val timelineLaneWidth: androidx.compose.ui.unit.Dp = 20.dp,
    val timelineDetailHourHeight: androidx.compose.ui.unit.Dp = 72.dp,
    val timelineMinimumBlockHeight: androidx.compose.ui.unit.Dp = 3.dp,
    val timerDigitWidth: androidx.compose.ui.unit.Dp = 46.dp,
    val timerDigitHeight: androidx.compose.ui.unit.Dp = 74.dp,
    val timerDigitGap: androidx.compose.ui.unit.Dp = 3.dp,
    val timerCameraDistance: androidx.compose.ui.unit.Dp = 12.dp,
    val pickerHeight: androidx.compose.ui.unit.Dp = 114.dp,
    val pickerItemHeight: androidx.compose.ui.unit.Dp = 38.dp,
    val calendarRowHeight: androidx.compose.ui.unit.Dp = 44.dp,
    val navigationPrimaryHeight: androidx.compose.ui.unit.Dp = 32.dp,
    val navigationItemMinWidth: androidx.compose.ui.unit.Dp = 56.dp,
    val navigationSecondaryHeight: androidx.compose.ui.unit.Dp = 28.dp,
    val dateNavigationHeight: androidx.compose.ui.unit.Dp = 28.dp,
    /** Fixed total height of the secondary navigation band (level-two row + date row).
     *  Pages without a level-two row still reserve the slot, so Plan and Statistics keep
     *  an identical band height and switching between them never jumps. */
    val navBandHeight: androidx.compose.ui.unit.Dp =
        navigationSecondaryHeight + spaceS + dateNavigationHeight,
    val segmentedHorizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    val compactCardPadding: androidx.compose.ui.unit.Dp = 10.dp,
    /** Shared visible chip and management-row sizes. Callers provide their own touch regions. */
    val tagChipHeight: androidx.compose.ui.unit.Dp = 32.dp,
    val tagChipGap: androidx.compose.ui.unit.Dp = 6.dp,
    val tagChipHorizontalPadding: androidx.compose.ui.unit.Dp = 10.dp,
    val tagRootChipHorizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    val tagRowHeight: androidx.compose.ui.unit.Dp = 40.dp,
    val tagRowGap: androidx.compose.ui.unit.Dp = 4.dp,
    val tagSectionGap: androidx.compose.ui.unit.Dp = 6.dp,
    val reorderRowTouchHeight: androidx.compose.ui.unit.Dp = 44.dp,
    val reorderRowHeight: androidx.compose.ui.unit.Dp = 42.dp,
    val reorderRowGap: androidx.compose.ui.unit.Dp = 2.dp,
    val reorderHandleIconSize: androidx.compose.ui.unit.Dp = 20.dp,
    val reorderAutoScrollEdge: androidx.compose.ui.unit.Dp = 52.dp,
    val reorderAutoScrollStep: androidx.compose.ui.unit.Dp = 20.dp,
    /** Gap between the home page's date header and its timeline. */
    val todayHeaderContentGap: androidx.compose.ui.unit.Dp = 2.dp,
    /** Home date row: must fit the 48sp number. */
    val todayHeaderHeight: androidx.compose.ui.unit.Dp = 36.dp,
    /** Other pages: only a 32dp capsule, so the container can be tighter. */
    val compactHeaderHeight: androidx.compose.ui.unit.Dp = 36.dp,
    /** Plan page: gap from date row to first content element, per view. */
    val planDayBoardGap: androidx.compose.ui.unit.Dp = 4.dp,   // 日视图 / 周计划 / TodoDone
    val planMonthGap: androidx.compose.ui.unit.Dp = 2 .dp,      // 月视图（自带上 20dp 切换按钮）
    val settingsRowHeight: Dp = 44.dp,
)

val LocalAppDimens = staticCompositionLocalOf { AppDimens() }

