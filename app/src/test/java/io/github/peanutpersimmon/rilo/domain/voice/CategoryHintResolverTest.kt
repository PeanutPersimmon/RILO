package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryHintResolverTest {
    @Test
    fun exactCategoryNameWinsBeforeKeywordLayers() {
        val result = CategoryHintResolver.resolve(
            categoryHint = " 科研 ",
            categories = listOf(category(1, "科研", 4), category(2, "工作", 0)),
            rules = listOf(rule(1, "科研", 2, priority = 99)),
        )

        assertEquals(1L, result.categoryId)
    }

    @Test
    fun exactKeywordWinsBeforeContainedKeyword() {
        val result = CategoryHintResolver.resolve(
            categoryHint = "做实验",
            categories = listOf(category(1, "科研", 1), category(2, "工作", 0)),
            rules = listOf(
                rule(1, "实验", 1, priority = 100),
                rule(2, "做实验", 2, priority = 0),
            ),
        )

        assertEquals(2L, result.categoryId)
    }

    @Test
    fun containedLayerUsesPriorityLengthSortOrderAndRuleId() {
        val categories = listOf(
            category(1, "一", 9),
            category(2, "二", 1),
            category(3, "三", 1),
        )
        val priorityWinner = CategoryHintResolver.resolve(
            "准备做实验记录",
            categories,
            listOf(rule(10, "做实验", 1, 1), rule(20, "实验记录", 2, 2)),
        )
        assertEquals(2L, priorityWinner.categoryId)

        val lengthWinner = CategoryHintResolver.resolve(
            "准备做实验记录",
            categories,
            listOf(rule(10, "实验", 1, 2), rule(20, "实验记录", 2, 2)),
        )
        assertEquals(2L, lengthWinner.categoryId)

        val sortWinner = CategoryHintResolver.resolve(
            "准备做实验",
            categories,
            listOf(rule(10, "实验", 1, 2), rule(20, "实验", 2, 2)),
        )
        assertEquals(2L, sortWinner.categoryId)

        val idWinner = CategoryHintResolver.resolve(
            "准备做实验",
            categories,
            listOf(rule(30, "实验", 3, 2), rule(20, "实验", 2, 2)),
        )
        assertEquals(2L, idWinner.categoryId)
    }

    @Test
    fun legacyArchivedCategoryIsClassifiableAndNoMatchStaysEmpty() {
        val active = category(1, "科研", 0)
        val archived = category(2, "旧科研", 0, archived = true)
        assertNull(CategoryHintResolver.resolve("会", listOf(active), listOf(rule(1, "组会", 1))).categoryId)
        assertEquals(2L, CategoryHintResolver.resolve("实验", listOf(archived), listOf(rule(2, "实验", 2))).categoryId)
        assertNull(CategoryHintResolver.resolve("未知", listOf(active), listOf(rule(1, "组会", 1))).categoryId)
    }

    private fun category(id: Long, name: String, order: Int, archived: Boolean = false) = Category(
        id = id,
        name = name,
        isArchived = archived,
        sortOrder = order,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )

    private fun rule(
        id: Long,
        keyword: String,
        categoryId: Long,
        priority: Int = 0,
    ) = KeywordRule(
        id = id,
        keyword = keyword,
        categoryId = categoryId,
        priority = priority,
        createdAtMillis = 0,
        updatedAtMillis = 0,
    )
}
