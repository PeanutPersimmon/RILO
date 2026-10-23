package io.github.peanutpersimmon.rilo.presentation.editor.event

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorClassificationTest {
    private val dispatcher = StandardTestDispatcher()
    private val day = LocalDate.of(2026, 10, 19)
    private val categories = listOf(
        Category(id = 1, name = "科研", sortOrder = 2, createdAtMillis = 0, updatedAtMillis = 0),
        Category(id = 2, name = "生活", sortOrder = 1, createdAtMillis = 0, updatedAtMillis = 0),
        Category(id = 3, name = "工作", sortOrder = 3, createdAtMillis = 0, updatedAtMillis = 0),
    )
    private val rules = listOf(
        KeywordRule(id = 1, keyword = "论文", categoryId = 1, createdAtMillis = 0, updatedAtMillis = 0),
        KeywordRule(id = 2, keyword = "买菜", categoryId = 2, createdAtMillis = 0, updatedAtMillis = 0),
    )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun editingExistingEventRematchesTitleAndKeepsOriginalWhenUnmatched() = runTest {
        val editor = editor()
        editor.load(7)
        advanceUntilIdle()
        editor.changeTitle("去买菜")
        advanceUntilIdle()
        assertEquals(2L, editor.uiState.value.categoryId)
        editor.changeTitle("散步")
        advanceUntilIdle()
        assertEquals(2L, editor.uiState.value.categoryId)
    }

    @Test fun manualCategoryChoiceDuringSessionWinsOverLaterTitleChanges() = runTest {
        val editor = editor()
        editor.load(7)
        advanceUntilIdle()
        editor.chooseCategory(3)
        editor.changeTitle("去买菜")
        advanceUntilIdle()
        assertEquals(3L, editor.uiState.value.categoryId)
    }

    @Test fun unmatchedNewTitleDoesNotInventCategory() = runTest {
        val editor = editor()
        editor.load(null)
        advanceUntilIdle()
        editor.changeTitle("散步")
        advanceUntilIdle()
        assertEquals(null, editor.uiState.value.categoryId)
    }

    @Test fun equalPriorityKeywordConflictUsesExistingSortOrder() = runTest {
        val editor = editor(rules + KeywordRule(id = 3, keyword = "买菜", categoryId = 1,
            createdAtMillis = 0, updatedAtMillis = 0))
        editor.load(null)
        advanceUntilIdle()
        editor.changeTitle("去买菜")
        advanceUntilIdle()
        assertEquals(2L, editor.uiState.value.categoryId)
    }

    private fun editor(keywordRules: List<KeywordRule> = rules): EventEditorViewModel {
        val existing = Event(id = 7, title = "写论文", dateEpochDay = day.toEpochDay(),
            categoryId = 1, createdAtMillis = 0, updatedAtMillis = 0)
        val repository = Proxy.newProxyInstance(RidoCoreRepository::class.java.classLoader,
            arrayOf(RidoCoreRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getEvent" -> existing.takeIf { args!![0] == 7L }
                "getCategories" -> categories
                "getEnabledKeywordRules" -> keywordRules
                else -> error("Unexpected repository call: ${method.name}")
            }
        } as RidoCoreRepository
        return EventEditorViewModel(GetEventForEditorUseCase(repository), GetEditorOptionsUseCase(repository),
            SaveEventUseCase(repository), DeleteEventUseCase(repository), RestoreEventUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository))
    }
}
