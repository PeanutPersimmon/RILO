package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class AppTypographyTokens(
    val pageTitle: TextStyle,
    val sectionTitle: TextStyle,
    val itemTitle: TextStyle,
    val body: TextStyle,
    val secondary: TextStyle,
    val caption: TextStyle,
    val time: TextStyle,
    val number: TextStyle,
    val sheetTitle: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    val editorInput: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    val editorDescription: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    val editorTool: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val fieldLabel: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val fieldValue: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    val timelineTitle: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    val timelineDuration: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 12.sp),
    val timelineMetadata: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 13.sp),
    val timelineTime: TextStyle = TextStyle(fontSize = 9.sp, lineHeight = 12.sp),
    val timelineReminder: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
    val reminderItemTitle: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    val reminderMetadata: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 13.sp),
    val navigationPrimary: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    val navigationSecondary: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val periodTitle: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    val compactCardTitle: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    val compactSectionTitle: TextStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    val bodyCompact: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    val chartLabel: TextStyle = TextStyle(fontSize = 9.sp, lineHeight = 12.sp),
    val assistive: TextStyle = TextStyle(fontSize = 10.sp, lineHeight = 14.sp),
)

internal val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    displaySmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
)

internal val AppTypeTokens = AppTypographyTokens(
    pageTitle = AppTypography.headlineLarge,
    sectionTitle = AppTypography.titleLarge,
    itemTitle = AppTypography.titleMedium,
    body = AppTypography.bodyLarge,
    secondary = AppTypography.bodyMedium,
    caption = AppTypography.labelMedium,
    time = AppTypography.labelLarge,
    number = AppTypography.displaySmall,
)

val LocalAppTypography = staticCompositionLocalOf { AppTypeTokens }
