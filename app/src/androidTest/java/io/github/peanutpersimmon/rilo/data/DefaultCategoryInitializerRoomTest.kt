package io.github.peanutpersimmon.rilo.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rilo.data.local.room.DefaultCategoriesMarker
import io.github.peanutpersimmon.rilo.data.local.room.DefaultCategoryInitializer
import io.github.peanutpersimmon.rilo.data.local.room.DefaultCategorySeedStore
import io.github.peanutpersimmon.rilo.data.local.room.FreshDatabaseMarker
import io.github.peanutpersimmon.rilo.data.local.room.FreshDatabaseMarkerCallback
import io.github.peanutpersimmon.rilo.data.local.room.InitialCategory
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.RoomDefaultCategorySeedStore
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.data.repository.RoomCategoryRepository
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultCategoryInitializerRoomTest {
    @Test
    fun freshDatabaseSeedsOrdinaryEditableRowsOnce() = runBlocking {
        withDatabase(fresh = true) { db ->
            val initializer = DefaultCategoryInitializer(db)
            initializer.initialize()
            initializer.initialize()
            val categories = db.categoryDao().getAllIncludingArchived()
            val rules = db.keywordRuleDao().getAll()
            val categoryIds = categories.map(CategoryEntity::id).toSet()
            assertEquals(8, categories.size)
            assertEquals(39, rules.size)
            assertEquals(listOf("blue", "purple", "orange", "green", "teal", "pink", "amber", "red"),
                categories.map { it.colorKey })
            assertTrue(categories.all { !it.isArchived })
            assertTrue(rules.all { it.categoryId != null && it.categoryId in categoryIds && it.isEnabled })
            assertTrue(db.processedCommandDao().exists(DefaultCategoriesMarker))

            val work = categories.first { it.name == "工作" }
            val repository = RoomCategoryRepository(db)
            repository.updateCategory(work.id, "职业", "green", work.sortOrder)
            val meeting = repository.observeCategoryKeywords(work.id).first().first { it.keyword == "会议" }
            repository.deleteCategoryKeyword(work.id, meeting.id)
            repository.addCategoryKeyword(work.id, "新项目")
            initializer.initialize()
            assertEquals("职业", db.categoryDao().getById(work.id)?.name)
            assertEquals("green", db.categoryDao().getById(work.id)?.colorKey)
            assertEquals(8, db.categoryDao().getAllIncludingArchived().size)
            assertEquals(setOf("工作", "汇报", "项目", "邮件", "新项目"),
                repository.observeCategoryKeywords(work.id).first().mapTo(mutableSetOf()) { it.keyword })
        }
    }

    @Test
    fun oldEmptyDatabaseAndOldTemplateMarkerNeverTriggerSeed() = runBlocking {
        withDatabase(fresh = false) { db ->
            db.processedCommandDao().insert(
                ProcessedCommandEntity("category-keyword-template:research:v1", 1L))
            DefaultCategoryInitializer(db).initialize()
            assertTrue(db.categoryDao().getAllIncludingArchived().isEmpty())
            assertTrue(db.keywordRuleDao().getAll().isEmpty())
            assertTrue(db.processedCommandDao().exists("category-keyword-template:research:v1"))
            assertTrue(db.processedCommandDao().exists(DefaultCategoriesMarker))
            assertFalse(db.processedCommandDao().exists(FreshDatabaseMarker))
        }
    }

    @Test
    fun existingCategoryAndRuleRemainUnchanged() = runBlocking {
        withDatabase(fresh = false) { db ->
            val categoryId = db.categoryDao().insert(CategoryEntity(name = "自建", colorKey = "olive",
                createdAtMillis = 1L, updatedAtMillis = 1L))
            db.keywordRuleDao().insert(KeywordRuleEntity(keyword = "原词", categoryId = categoryId,
                createdAtMillis = 1L, updatedAtMillis = 1L))
            DefaultCategoryInitializer(db).initialize()
            assertEquals(listOf("自建"), db.categoryDao().getAllIncludingArchived().map { it.name })
            assertEquals(listOf("原词"), db.keywordRuleDao().getAll().map { it.keyword })
            assertTrue(db.processedCommandDao().exists(DefaultCategoriesMarker))
        }
    }

    @Test
    fun eventSessionOrKeywordDataPreventsSeedEvenWithFreshOriginMarker() = runBlocking {
        for (kind in listOf("event", "session", "keyword")) {
            withDatabase(fresh = true) { db ->
                when (kind) {
                    "event" -> db.eventDao().insert(EventEntity(title = "旧日程", dateEpochDay = 1L,
                        createdAtMillis = 1L, updatedAtMillis = 1L))
                    "session" -> db.sessionDao().insert(SessionEntity(title = "旧记录", kind = SessionKind.ACTIVITY,
                        temporalType = SessionTemporalType.POINT, startAtMillis = 1L,
                        createdAtMillis = 1L, updatedAtMillis = 1L))
                    else -> db.keywordRuleDao().insert(KeywordRuleEntity(keyword = "原词",
                        createdAtMillis = 1L, updatedAtMillis = 1L))
                }
                DefaultCategoryInitializer(db).initialize()
                assertTrue("$kind should skip seed", db.categoryDao().getAllIncludingArchived().isEmpty())
                assertTrue(db.processedCommandDao().exists(DefaultCategoriesMarker))
            }
        }
    }

    @Test
    fun failedInsertRollsBackAllRowsAndCompletionMarkerThenRetrySucceeds() = runBlocking {
        withDatabase(fresh = true) { db ->
            val roomStore = RoomDefaultCategorySeedStore(db)
            val failingStore = object : DefaultCategorySeedStore by roomStore {
                override suspend fun insertKeyword(categoryId: Long, keyword: String, now: Long) {
                    if (keyword == "课程") error("Simulated failure")
                    roomStore.insertKeyword(categoryId, keyword, now)
                }
            }
            assertTrue(runCatching { DefaultCategoryInitializer(failingStore).initialize() }.isFailure)
            assertTrue(db.categoryDao().getAllIncludingArchived().isEmpty())
            assertTrue(db.keywordRuleDao().getAll().isEmpty())
            assertFalse(db.processedCommandDao().exists(DefaultCategoriesMarker))
            assertTrue(db.processedCommandDao().exists(FreshDatabaseMarker))
            DefaultCategoryInitializer(db).initialize()
            assertEquals(8, db.categoryDao().getAllIncludingArchived().size)
            assertEquals(39, db.keywordRuleDao().getAll().size)
        }
    }

    private suspend fun withDatabase(fresh: Boolean, block: suspend (RidoDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val builder = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java)
        if (fresh) builder.addCallback(FreshDatabaseMarkerCallback)
        val db = builder.build()
        try { block(db) } finally { db.close() }
    }
}
