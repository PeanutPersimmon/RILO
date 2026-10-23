package io.github.peanutpersimmon.rilo.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.peanutpersimmon.rilo.R

enum class RootDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Today(R.string.nav_today, Icons.Outlined.Today),
    Plan(R.string.nav_plan, Icons.AutoMirrored.Outlined.EventNote),
    Statistics(R.string.nav_statistics, Icons.Outlined.BarChart),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}
