package io.github.peanutpersimmon.rilo.data.local.room

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoryInitializerTest {
    @Test
    fun freshDatabaseCreatesEightOrdinaryCategoriesAndAllLinkedRulesExactlyOnce() = runBlocking {
        val store = FakeStore(fresh = true)
        val initializer = DefaultCategoryInitializer(store) { 123L }

        initializer.initialize()
        initializer.initialize()

        assertEquals(listOf("工作" to "blue", "学习" to "purple", "生活" to "orange",
            "运动" to "green", "出行" to "teal", "社交" to "pink", "娱乐" to "amber",
            "健康" to "red"), store.categories.map { it.second.name to it.second.colorKey })
        assertEquals((0..7).toList(), store.sortOrders)
        assertEquals(39, store.keywords.size)
        assertEquals(mapOf(
            "工作" to listOf("工作", "会议", "汇报", "项目", "邮件"),
            "学习" to listOf("学习", "文献", "课程", "论文", "阅读"),
            "生活" to listOf("吃饭", "做饭", "家务", "快递", "购物"),
            "运动" to listOf("跑步", "健身", "散步", "骑行", "游泳"),
            "出行" to listOf("通勤", "地铁", "公交", "高铁", "飞机"),
            "社交" to listOf("聚餐", "聚会", "见面", "约会"),
            "娱乐" to listOf("电影", "游戏", "视频", "音乐", "娱乐"),
            "健康" to listOf("医院", "看病", "体检", "吃药", "挂号"),
        ), store.categories.associate { (id, category) ->
            category.name to store.keywords.filter { it.first == id }.map { it.second }
        })
        assertTrue(DefaultCategoriesMarker in store.markers)
        assertEquals(1, store.markers.count { it == DefaultCategoriesMarker })
    }

    @Test
    fun completedMarkerPreventsCreationEvenWhenOriginMarkerExists() = runBlocking {
        val store = FakeStore(fresh = true)
        store.markers += DefaultCategoriesMarker
        DefaultCategoryInitializer(store).initialize()
        assertTrue(store.categories.isEmpty())
        assertTrue(store.keywords.isEmpty())
    }

    @Test
    fun emptyCategoriesAfterCompletedInitializationAreNotRestored() = runBlocking {
        val store = FakeStore(fresh = true)
        val initializer = DefaultCategoryInitializer(store)
        initializer.initialize()
        store.categories.clear()
        store.keywords.clear()
        initializer.initialize()
        assertTrue(store.categories.isEmpty())
        assertTrue(store.keywords.isEmpty())
    }

    @Test
    fun oldEmptyDatabaseIsNeverMistakenForFreshInstallation() = runBlocking {
        val store = FakeStore(fresh = false)
        DefaultCategoryInitializer(store).initialize()
        assertTrue(store.categories.isEmpty())
        assertTrue(store.keywords.isEmpty())
        assertTrue(DefaultCategoriesMarker in store.markers)
    }

    @Test
    fun priorCategoryOrOtherBusinessDataSkipsSeedAndPreservesData() = runBlocking {
        val categoryStore = FakeStore(fresh = true)
        categoryStore.categories += 42L to InitialCategory("已有分类", "neutral", listOf("旧词"))
        categoryStore.keywords += 42L to "旧词"
        DefaultCategoryInitializer(categoryStore).initialize()
        assertEquals(listOf("已有分类"), categoryStore.categories.map { it.second.name })
        assertEquals(listOf(42L to "旧词"), categoryStore.keywords)
        assertTrue(DefaultCategoriesMarker in categoryStore.markers)

        val businessStore = FakeStore(fresh = true).apply { otherBusinessData = true }
        DefaultCategoryInitializer(businessStore).initialize()
        assertTrue(businessStore.categories.isEmpty())
        assertTrue(businessStore.keywords.isEmpty())
        assertTrue(DefaultCategoriesMarker in businessStore.markers)
    }

    @Test
    fun failureRollsBackCategoriesRulesAndCompletionMarkerThenRetrySucceeds() = runBlocking {
        val store = FakeStore(fresh = true).apply { failOnKeyword = "课程" }
        val initializer = DefaultCategoryInitializer(store)
        assertTrue(runCatching { initializer.initialize() }.isFailure)
        assertTrue(store.categories.isEmpty())
        assertTrue(store.keywords.isEmpty())
        assertFalse(DefaultCategoriesMarker in store.markers)
        assertTrue(FreshDatabaseMarker in store.markers)

        store.failOnKeyword = null
        initializer.initialize()
        assertEquals(8, store.categories.size)
        assertEquals(39, store.keywords.size)
        assertTrue(DefaultCategoriesMarker in store.markers)
    }

    private class FakeStore(fresh: Boolean) : DefaultCategorySeedStore {
        val categories = mutableListOf<Pair<Long, InitialCategory>>()
        val sortOrders = mutableListOf<Int>()
        val keywords = mutableListOf<Pair<Long, String>>()
        val markers = mutableSetOf<String>().apply { if (fresh) add(FreshDatabaseMarker) }
        var otherBusinessData = false
        var failOnKeyword: String? = null

        override suspend fun transaction(block: suspend () -> Unit) {
            val priorCategories = categories.toList()
            val priorOrders = sortOrders.toList()
            val priorKeywords = keywords.toList()
            val priorMarkers = markers.toSet()
            try {
                block()
            } catch (error: Throwable) {
                categories.clear(); categories += priorCategories
                sortOrders.clear(); sortOrders += priorOrders
                keywords.clear(); keywords += priorKeywords
                markers.clear(); markers += priorMarkers
                throw error
            }
        }

        override suspend fun hasMarker(marker: String) = marker in markers
        override suspend fun hasPriorUserData() = otherBusinessData || categories.isNotEmpty() ||
            keywords.isNotEmpty() || markers.any { it !in setOf(FreshDatabaseMarker, DefaultCategoriesMarker) }
        override suspend fun insertCategory(category: InitialCategory, sortOrder: Int, now: Long): Long {
            val id = categories.size + 1L
            categories += id to category
            sortOrders += sortOrder
            return id
        }
        override suspend fun insertKeyword(categoryId: Long, keyword: String, now: Long) {
            if (keyword == failOnKeyword) error("Simulated insert failure")
            keywords += categoryId to keyword
        }
        override suspend fun insertMarker(marker: String, now: Long) {
            markers += marker
        }
    }
}
