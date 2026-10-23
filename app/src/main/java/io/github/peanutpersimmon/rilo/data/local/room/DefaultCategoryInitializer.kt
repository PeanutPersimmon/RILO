package io.github.peanutpersimmon.rilo.data.local.room

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey

internal const val FreshDatabaseMarker = "rilo_new_database_v1"
internal const val DefaultCategoriesMarker = "rilo_default_categories_v1"

internal data class InitialCategory(val name: String, val colorKey: String, val keywords: List<String>)

internal val initialCategories = listOf(
    InitialCategory("工作", CategoryColorKey.BLUE.storageKey, listOf("工作", "会议", "汇报", "项目", "邮件")),
    InitialCategory("学习", CategoryColorKey.PURPLE.storageKey, listOf("学习", "文献", "课程", "论文", "阅读")),
    InitialCategory("生活", CategoryColorKey.ORANGE.storageKey, listOf("吃饭", "做饭", "家务", "快递", "购物")),
    InitialCategory("运动", CategoryColorKey.GREEN.storageKey, listOf("跑步", "健身", "散步", "骑行", "游泳")),
    InitialCategory("出行", CategoryColorKey.TEAL.storageKey, listOf("通勤", "地铁", "公交", "高铁", "飞机")),
    InitialCategory("社交", CategoryColorKey.PINK.storageKey, listOf("聚餐", "聚会", "见面", "约会")),
    InitialCategory("娱乐", CategoryColorKey.AMBER.storageKey, listOf("电影", "游戏", "视频", "音乐", "娱乐")),
    InitialCategory("健康", CategoryColorKey.RED.storageKey, listOf("医院", "看病", "体检", "吃药", "挂号")),
)

/** All calls, including the final marker, must run inside one database transaction. */
internal interface DefaultCategorySeedStore {
    suspend fun transaction(block: suspend () -> Unit)
    suspend fun hasMarker(marker: String): Boolean
    suspend fun hasPriorUserData(): Boolean
    suspend fun insertCategory(category: InitialCategory, sortOrder: Int, now: Long): Long
    suspend fun insertKeyword(categoryId: Long, keyword: String, now: Long)
    suspend fun insertMarker(marker: String, now: Long)
}

internal class DefaultCategoryInitializer(
    private val store: DefaultCategorySeedStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(database: RidoDatabase) : this(RoomDefaultCategorySeedStore(database))

    suspend fun initialize() = store.transaction {
        if (store.hasMarker(DefaultCategoriesMarker)) return@transaction
        if (store.hasMarker(FreshDatabaseMarker) && !store.hasPriorUserData()) {
            val now = clock()
            initialCategories.forEachIndexed { order, category ->
                val id = store.insertCategory(category, order, now)
                category.keywords.forEach { store.insertKeyword(id, it, now) }
            }
        }
        store.insertMarker(DefaultCategoriesMarker, clock())
    }
}

internal class RoomDefaultCategorySeedStore(private val database: RidoDatabase) : DefaultCategorySeedStore {
    private val categories = database.categoryDao()
    private val keywords = database.keywordRuleDao()
    private val markers = database.processedCommandDao()

    override suspend fun transaction(block: suspend () -> Unit) = database.withTransaction { block() }
    override suspend fun hasMarker(marker: String) = markers.exists(marker)
    override suspend fun hasPriorUserData() =
        markers.hasPriorUserData(FreshDatabaseMarker, DefaultCategoriesMarker)
    override suspend fun insertCategory(category: InitialCategory, sortOrder: Int, now: Long): Long =
        categories.insert(CategoryEntity(
            name = category.name,
            sortOrder = sortOrder,
            createdAtMillis = now,
            updatedAtMillis = now,
            colorKey = category.colorKey,
        ))
    override suspend fun insertKeyword(categoryId: Long, keyword: String, now: Long) {
        keywords.insert(KeywordRuleEntity(
            keyword = keyword,
            categoryId = categoryId,
            createdAtMillis = now,
            updatedAtMillis = now,
        ))
    }
    override suspend fun insertMarker(marker: String, now: Long) {
        markers.insert(ProcessedCommandEntity(marker, now))
    }
}
