package io.github.peanutpersimmon.rilo.presentation.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class PlanViewOrderTest {
    private val customOrder = listOf(
        PlanViewMode.BOARD,
        PlanViewMode.MONTH,
        PlanViewMode.DAY,
        PlanViewMode.WEEK,
        PlanViewMode.TODO_DONE,
    )

    @Test fun discreteSwipeUsesCurrentPreferenceOrderWithoutWrapping() {
        val selection = initialPlanRuntimeSelection(customOrder, LocalDate.of(2026, 10, 17))
            .withMode(PlanViewMode.DAY)

        assertEquals(
            PlanViewMode.WEEK,
            selection.withAdjacentMode(customOrder, 1).mode,
        )
        assertEquals(
            PlanViewMode.MONTH,
            selection.withAdjacentMode(customOrder, -1).mode,
        )
        assertEquals(
            PlanViewMode.BOARD,
            selection.withMode(PlanViewMode.BOARD).withAdjacentMode(customOrder, -1).mode,
        )
        assertEquals(
            PlanViewMode.TODO_DONE,
            selection.withMode(PlanViewMode.TODO_DONE).withAdjacentMode(customOrder, 1).mode,
        )
    }

    @Test fun changedSelectorOrderIsAlsoTheSwipeOrderWithoutResettingSelection() {
        val selection = initialPlanRuntimeSelection(
            PlanViewModel.DEFAULT_VIEW_ORDER,
            LocalDate.of(2026, 10, 17),
        )
            .withMode(PlanViewMode.DAY)

        val afterOrderChange = selection
        val afterSwipe = afterOrderChange.withAdjacentMode(customOrder, 1)

        assertEquals(PlanViewMode.DAY, afterOrderChange.mode)
        assertEquals(PlanViewMode.WEEK, afterSwipe.mode)
        assertEquals(customOrder, initialPlanViewOrder(customOrder.map { it.name }))
    }

    @Test fun persistedFirstViewInitializesNewPlanRuntimeState() {
        val today = LocalDate.of(2026, 10, 17)
        val board = initialPlanRuntimeSelection(
            initialPlanViewOrder(listOf("BOARD", "DAY", "WEEK", "MONTH", "TODO_DONE")), today)
        val month = initialPlanRuntimeSelection(
            initialPlanViewOrder(listOf("MONTH", "BOARD", "DAY", "WEEK", "TODO_DONE")), today)

        assertEquals(PlanViewMode.BOARD, board.mode)
        assertEquals(PlanViewMode.MONTH, month.mode)
    }

    @Test fun leavingRootPageResetsDatesButKeepsMode() {
        val november = LocalDate.of(2026, 11, 18)
        val initialized = initialPlanRuntimeSelection(
            PlanViewModel.DEFAULT_VIEW_ORDER, LocalDate.of(2026, 10, 17))
        val selected = initialized.withMode(PlanViewMode.MONTH)
            .withDate(november)

        val afterReturningToPlan = selected.resetDates(LocalDate.of(2026, 10, 19))
        assertEquals(PlanViewMode.MONTH, afterReturningToPlan.mode)
        assertEquals(LocalDate.of(2026, 10, 19), afterReturningToPlan.selectedDate)
        assertEquals(5, afterReturningToPlan.dates.size)
    }

    @Test fun selectorContainsExactlyFiveViews() {
        assertEquals(5, PlanViewMode.entries.size)
        assertEquals(PlanViewMode.entries.toSet(), PlanViewModel.DEFAULT_VIEW_ORDER.toSet())
        assertFalse(PlanViewMode.entries.any { it.name == "TASK_LIST" })
    }

    @Test fun oldTaskListFirstFallsBackToBoard() {
        val order = normalizedPlanViewOrder(listOf("TASK_LIST", "DAY", "WEEK", "BOARD", "TODO_DONE", "MONTH"))
        assertEquals(PlanViewMode.BOARD, order.first())
        assertEquals(PlanViewMode.entries.toSet(), order.toSet())
        assertEquals(5, order.size)
    }

    @Test fun oldTaskListInMiddleIsDroppedWithoutChangingFirstView() {
        val order = normalizedPlanViewOrder(listOf("WEEK", "TASK_LIST", "DAY", "BOARD", "TODO_DONE", "MONTH"))
        assertEquals(PlanViewMode.WEEK, order.first())
        assertEquals(5, order.size)
    }
}
