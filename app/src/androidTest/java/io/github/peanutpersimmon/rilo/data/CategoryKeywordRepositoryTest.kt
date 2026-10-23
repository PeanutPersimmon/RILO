package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.data.repository.RoomCategoryRepository
import io.github.peanutpersimmon.rilo.domain.model.CategoryKeywordConflictException
import io.github.peanutpersimmon.rilo.domain.model.ReservedCategoryKeywordException
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryKeywordRepositoryTest {
    @Test
    fun batchInsertSucceedsAndUsesDefaultPriority() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("科研")

            repository.addCategoryKeywords(categoryId, listOf("论文", "实验", "写作"))

            val rules = repository.observeCategoryKeywords(categoryId).first()
            assertEquals(setOf("论文", "实验", "写作"), rules.mapTo(mutableSetOf()) { it.keyword })
            assertTrue(rules.all { it.priority == 0 })
        }
    }

    @Test
    fun batchConflictRollsBackEveryKeyword() = runBlocking {
        withRepository { _, repository ->
            val existing = repository.createCategory("工作")
            repository.addCategoryKeyword(existing, "组会")
            val target = repository.createCategory("科研")

            assertTrue(
                runCatching {
                    repository.addCategoryKeywords(target, listOf("论文", "组会", "实验"))
                }.exceptionOrNull() is CategoryKeywordConflictException,
            )
            assertTrue(repository.observeCategoryKeywords(target).first().isEmpty())
        }
    }

    @Test
    fun batchReservedKeywordRollsBackEveryKeyword() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("科研")

            assertTrue(
                runCatching {
                    repository.addCategoryKeywords(categoryId, listOf("论文", "午睡", "实验"))
                }.exceptionOrNull() is ReservedCategoryKeywordException,
            )
            assertTrue(repository.observeCategoryKeywords(categoryId).first().isEmpty())
        }
    }

    @Test
    fun normalizedKeywordIsGloballyUniqueAndSelfEditIsAllowed() = runBlocking {
        withRepository { _, repository ->
            val first = repository.createCategory("工作")
            val second = repository.createCategory("学习")
            val ruleId = repository.addCategoryKeyword(first, " ＭＥＥＴＩＮＧ  PLAN ")

            repository.updateCategoryKeyword(first, ruleId, "meeting plan", priority = 2)

            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "meeting\tplan") }
                    .exceptionOrNull() is CategoryKeywordConflictException,
            )
        }
    }

    @Test
    fun duplicateActiveKeywordAndSleepVocabularyAreRejected() = runBlocking {
        withRepository { _, repository ->
            val first = repository.createCategory("工作")
            val second = repository.createCategory("学习")
            repository.addCategoryKeyword(first, "组会")

            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "  组会  ") }
                    .exceptionOrNull() is CategoryKeywordConflictException,
            )
            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "午睡") }
                    .exceptionOrNull() is ReservedCategoryKeywordException,
            )
        }
    }

    @Test
    fun legacyArchivedRulesStayActiveWithoutChangingUserEnabledFlag() = runBlocking {
        withRepository { database, repository ->
            val categoryId = repository.createCategory("科研")
            val ruleId = repository.addCategoryKeyword(categoryId, "论文")
            val original = database.categoryDao().getById(categoryId)!!
            database.categoryDao().update(original.copy(isArchived = true))

            assertEquals(listOf(ruleId), database.keywordRuleDao().getEnabledCategoryRules().map { it.id })
            assertTrue(database.keywordRuleDao().getById(ruleId)?.isEnabled == true)
            assertEquals(1, repository.observeCategoryKeywords(categoryId).first().size)
            assertEquals(false, repository.observeCategories().first().single().isArchived)
        }
    }

    @Test
    fun legacyArchivedCategoryCanBeEditedReorderedAndDeleted() = runBlocking {
        withRepository { database, repository ->
            val first = repository.createCategory("历史分类", keywords = listOf("历史关键词"))
            val second = repository.createCategory("其它分类")
            database.categoryDao().update(database.categoryDao().getById(first)!!.copy(isArchived = true))
            assertEquals(listOf(first, second), repository.observeCategories().first().map { it.id })
            repository.updateCategory(first, "可编辑分类", "blue", 0)
            repository.reorderCategories(listOf(second, first))
            assertEquals(false, database.categoryDao().getById(first)?.isArchived)
            assertEquals("可编辑分类", repository.observeCategories().first().first { it.id == first }.name)
            assertEquals(1, database.keywordRuleDao().getEnabledCategoryRules().size)
            repository.deleteCategory(first)
            assertEquals(null, database.categoryDao().getById(first))
            assertTrue(database.keywordRuleDao().getAll().isEmpty())
        }
    }

    @Test
    fun userEditedKeywordDoesNotReturnAfterRename() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("学习", "purple", listOf("文献", "课程"))
            val rules = repository.observeCategoryKeywords(categoryId).first()
            repository.updateCategoryKeyword(categoryId, rules.first { it.keyword == "文献" }.id,
                "影像组学", 0)
            repository.deleteCategoryKeyword(categoryId, rules.first { it.keyword == "课程" }.id)
            repository.updateCategory(categoryId, "科研", "green", 0)
            repository.addCategoryKeyword(categoryId, "实验")
            assertEquals(setOf("影像组学", "实验"),
                repository.observeCategoryKeywords(categoryId).first().mapTo(mutableSetOf()) { it.keyword })
        }
    }

    @Test
    fun deletingUnusedCategoryRemovesCategoryAndItsKeywordRules() = runBlocking {
        withRepository { database, repository ->
            val categoryId = repository.createCategory("临时", keywords = listOf("临时事项"))
            val ruleId = repository.observeCategoryKeywords(categoryId).first().single().id

            assertEquals(0, repository.getCategoryUsage(categoryId).eventCount)
            assertEquals(0, repository.getCategoryUsage(categoryId).sessionCount)
            repository.deleteCategory(categoryId)

            assertEquals(null, database.categoryDao().getById(categoryId))
            assertEquals(null, database.keywordRuleDao().getById(ruleId))
        }
    }

    @Test
    fun deletingUsedCategoryPreservesEventAndSessionAndDoesNotAffectOtherCategory() = runBlocking {
        withRepository { database, repository ->
            val deletedId = repository.createCategory("工作", keywords = listOf("会议"))
            val keptId = repository.createCategory("学习", keywords = listOf("阅读"))
            val deletedRuleId = repository.observeCategoryKeywords(deletedId).first().single().id
            val keptRuleId = repository.observeCategoryKeywords(keptId).first().single().id
            val eventId = database.eventDao().insert(EventEntity(
                title = "例会",
                dateEpochDay = 1,
                categoryId = deletedId,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ))
            val sessionId = database.sessionDao().insert(SessionEntity(
                title = "例会记录",
                kind = SessionKind.ACTIVITY,
                temporalType = SessionTemporalType.INTERVAL,
                startAtMillis = 10,
                endAtMillis = 20,
                categoryId = deletedId,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ))

            val usage = repository.getCategoryUsage(deletedId)
            assertEquals(1, usage.eventCount)
            assertEquals(1, usage.sessionCount)
            repository.deleteCategory(deletedId)

            assertEquals(null, database.categoryDao().getById(deletedId))
            assertEquals(null, database.eventDao().getById(eventId)?.categoryId)
            assertEquals(null, database.sessionDao().getById(sessionId)?.categoryId)
            assertEquals(null, database.keywordRuleDao().getById(deletedRuleId))
            assertEquals("学习", database.categoryDao().getById(keptId)?.name)
            assertEquals(keptId, database.keywordRuleDao().getById(keptRuleId)?.categoryId)
        }
    }

    private suspend fun withRepository(
        test: suspend (RidoDatabase, RoomCategoryRepository) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            test(database, RoomCategoryRepository(database))
        } finally {
            database.close()
        }
    }
}
