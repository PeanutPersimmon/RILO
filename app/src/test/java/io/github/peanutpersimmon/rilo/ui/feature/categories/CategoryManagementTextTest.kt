package io.github.peanutpersimmon.rilo.ui.feature.categories

import io.github.peanutpersimmon.rilo.domain.repository.CategoryUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryManagementTextTest {
    @Test
    fun keywordCountAlwaysUsesCompactParenthesizedForm() {
        assertEquals("（7）", categoryKeywordCountText(7))
        assertEquals("（0）", categoryKeywordCountText(0))
    }

    @Test
    fun unusedCategoryConfirmationNamesCategoryAndKeywordRules() {
        val text = categoryDeleteConfirmationText("工作", CategoryUsage(0, 0))

        assertEquals("确定删除“工作”吗？\n该分类及其关键词规则将被删除。", text)
    }

    @Test
    fun usedCategoryConfirmationReportsCountsAndPreservation() {
        val text = categoryDeleteConfirmationText("工作", CategoryUsage(3, 12))

        assertTrue(text.contains("3 条日程和 12 条记录"))
        assertTrue(text.contains("不会删除这些内容"))
        assertTrue(text.contains("变为未分类"))
        assertTrue(text.contains("关键词规则也会被删除"))
    }
}
