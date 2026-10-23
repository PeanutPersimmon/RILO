package io.github.peanutpersimmon.rilo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rilo.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rilo.ui.components.TimelinePointItem
import io.github.peanutpersimmon.rilo.ui.components.TimelinePointSide
import io.github.peanutpersimmon.rilo.ui.theme.RidoTheme
import io.github.peanutpersimmon.rilo.ui.theme.LightSemanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelinePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pointKeepsEssentialTimeVisibleWithoutExpandingOtherDetails() {
        compose.setContent {
            RidoTheme {
                TimelinePointItem("会议", null, listOf("会议室"), 1f, {},
                    Modifier.width(220.dp), side = TimelinePointSide.PLANNED,
                    showMarker = false, alwaysVisibleMetadata = listOf("10:50"),
                    metadataProgress = 0f)
            }
        }
        compose.onNodeWithText("会议", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("10:50", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("会议室", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun categoryPointMarkersFollowSideAndKeepClickTargets() {
        var clicked = 0
        compose.setContent {
            RidoTheme {
                Column {
                    TimelinePointItem("计划点", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("planned_point"),
                        side = TimelinePointSide.PLANNED, markerColor = Color.Red)
                    TimelinePointItem("事实点", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("actual_point"),
                        side = TimelinePointSide.ACTUAL, markerColor = Color.Blue)
                    TimelinePointItem("无分类", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("unassigned_point"),
                        side = TimelinePointSide.ACTUAL, showMarker = false)
                }
            }
        }
        val markers = compose.onAllNodesWithTag("timeline_point_category_marker", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, markers.size)
        val planMarker = markers[0].boundsInRoot
        val actualMarker = markers[1].boundsInRoot
        assertTrue(planMarker.left > actualMarker.left)
        assertEquals(planMarker.width, planMarker.height, 0.1f)
        assertEquals(actualMarker.width, actualMarker.height, 0.1f)
        compose.onNodeWithTag("planned_point").performClick()
        compose.onNodeWithTag("actual_point").performClick()
        compose.onNodeWithTag("unassigned_point").performClick()
        compose.runOnIdle { assertEquals(3, clicked) }
    }

}
