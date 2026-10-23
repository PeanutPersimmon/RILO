package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.domain.model.ActiveCategoryNameConflictException
import io.github.peanutpersimmon.rilo.domain.model.BlankCategoryNameException
import io.github.peanutpersimmon.rilo.domain.model.BlankCategoryKeywordException
import io.github.peanutpersimmon.rilo.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rilo.domain.model.CategoryKeywordConflictException
import io.github.peanutpersimmon.rilo.domain.model.CategoryKeywordNotFoundException
import io.github.peanutpersimmon.rilo.domain.model.CategoryNotFoundException
import io.github.peanutpersimmon.rilo.domain.model.CategoryOrderChangedException
import io.github.peanutpersimmon.rilo.domain.model.InvalidCategoryColorKeyException
import io.github.peanutpersimmon.rilo.domain.model.ReservedCategoryKeywordException
import io.github.peanutpersimmon.rilo.domain.repository.CategoryRepository
import io.github.peanutpersimmon.rilo.domain.repository.CategoryUsage
import io.github.peanutpersimmon.rilo.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rilo.domain.service.ReservedKeywordPolicy
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(private val database: RidoDatabase) : CategoryRepository {
    private val dao = database.categoryDao()
    private val keywordDao = database.keywordRuleDao()

    override fun observeCategories() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createCategory(
        name: String,
        colorKey: String,
        keywords: List<String>,
    ): Long = database.withTransaction {
        val normalized = name.trim()
        if (normalized.isEmpty()) throw BlankCategoryNameException()
        requirePaletteColor(colorKey)
        val all = dao.getAllIncludingArchived()
        all.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            ?.let { throw ActiveCategoryNameConflictException(it.id) }
        val now = System.currentTimeMillis()
        val nextOrder = (all.maxOfOrNull(CategoryEntity::sortOrder) ?: -1) + 1
        val id = dao.insert(
            CategoryEntity(
                name = normalized,
                sortOrder = nextOrder,
                createdAtMillis = now,
                updatedAtMillis = now,
                colorKey = colorKey,
            ),
        )
        keywords.forEach { insertCategoryKeyword(id, it, priority = 0) }
        id
    }

    override suspend fun updateCategory(
        id: Long,
        name: String,
        colorKey: String,
        sortOrder: Int,
    ) = database.withTransaction {
        val existing = dao.getById(id) ?: throw CategoryNotFoundException(id)
        val normalized = name.trim()
        if (normalized.isEmpty()) throw BlankCategoryNameException()
        requirePaletteColor(colorKey)
        require(sortOrder >= 0) { "分类顺序无效" }
        dao.getActive().firstOrNull {
            it.id != id && it.name.equals(normalized, ignoreCase = true)
        }?.let { throw ActiveCategoryNameConflictException(it.id) }
        dao.update(
            existing.copy(
                name = normalized,
                colorKey = colorKey,
                sortOrder = sortOrder,
                isArchived = false,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun getCategoryUsage(id: Long): CategoryUsage {
        if (dao.getById(id) == null) throw CategoryNotFoundException(id)
        return CategoryUsage(
            eventCount = database.eventDao().countByCategoryId(id),
            sessionCount = database.sessionDao().countByCategoryId(id),
        )
    }

    override suspend fun deleteCategory(id: Long) = database.withTransaction {
        if (dao.getById(id) == null) throw CategoryNotFoundException(id)
        keywordDao.deleteByCategoryId(id)
        check(dao.deleteById(id) == 1) { "分类删除失败" }
    }

    override suspend fun reorderCategories(ids: List<Long>) = database.withTransaction {
        val active = dao.getActive()
        if (ids.size != active.size || ids.toSet() != active.map(CategoryEntity::id).toSet()) {
            throw CategoryOrderChangedException()
        }
        val now = System.currentTimeMillis()
        ids.forEachIndexed { index, id ->
            dao.update(active.first { it.id == id }.copy(sortOrder = index, updatedAtMillis = now))
        }
    }

    override fun observeCategoryKeywords(categoryId: Long) =
        keywordDao.observeCategoryRules(categoryId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addCategoryKeyword(
        categoryId: Long,
        keyword: String,
        priority: Int,
    ): Long = database.withTransaction {
        insertCategoryKeyword(categoryId, keyword, priority)
    }

    override suspend fun addCategoryKeywords(
        categoryId: Long,
        keywords: List<String>,
        priority: Int,
    ) = database.withTransaction {
        requireActiveCategory(categoryId)
        if (keywords.isEmpty()) return@withTransaction

        val canonicalKeywords = keywords.map(::requireKeyword)
        canonicalKeywords.forEachIndexed { index, keyword ->
            if (canonicalKeywords.take(index).any { it == keyword }) {
                throw CategoryKeywordConflictException(keyword, categoryId)
            }
            requireKeywordAvailable(keyword)
        }

        val now = System.currentTimeMillis()
        canonicalKeywords.forEach { keyword ->
            keywordDao.insert(
                KeywordRuleEntity(
                    keyword = keyword,
                    categoryId = categoryId,
                    isEnabled = true,
                    priority = priority,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }

    override suspend fun updateCategoryKeyword(
        categoryId: Long,
        id: Long,
        keyword: String,
        priority: Int,
    ) = database.withTransaction {
        requireActiveCategory(categoryId)
        val rule = keywordDao.getById(id) ?: throw CategoryKeywordNotFoundException(id)
        if (rule.categoryId != categoryId) throw CategoryKeywordNotFoundException(id)
        val canonical = requireKeyword(keyword)
        requireKeywordAvailable(canonical, excludingRuleId = id)
        keywordDao.update(
            rule.copy(
                keyword = canonical,
                priority = priority,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteCategoryKeyword(categoryId: Long, id: Long) = database.withTransaction {
        requireActiveCategory(categoryId)
        val rule = keywordDao.getById(id) ?: throw CategoryKeywordNotFoundException(id)
        if (rule.categoryId != categoryId) throw CategoryKeywordNotFoundException(id)
        keywordDao.delete(rule)
    }

    private suspend fun insertCategoryKeyword(
        categoryId: Long,
        keyword: String,
        priority: Int,
    ): Long {
        requireActiveCategory(categoryId)
        val canonical = requireKeyword(keyword)
        requireKeywordAvailable(canonical)
        val now = System.currentTimeMillis()
        return keywordDao.insert(
            KeywordRuleEntity(
                keyword = canonical,
                categoryId = categoryId,
                isEnabled = true,
                priority = priority,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    private fun requireKeyword(keyword: String): String {
        val canonical = CategoryKeywordNormalizer.normalize(keyword)
        if (canonical.isEmpty()) throw BlankCategoryKeywordException()
        if (ReservedKeywordPolicy.isReserved(canonical)) {
            throw ReservedCategoryKeywordException(canonical)
        }
        return canonical
    }

    private suspend fun requireKeywordAvailable(
        keyword: String,
        excludingRuleId: Long? = null,
    ) {
        findKeywordConflict(keyword, excludingRuleId)?.let { conflict ->
            throw CategoryKeywordConflictException(keyword, conflict.categoryId)
        }
    }

    private suspend fun findKeywordConflict(
        keyword: String,
        excludingRuleId: Long? = null,
    ): KeywordRuleEntity? {
        val normalized = CategoryKeywordNormalizer.normalize(keyword)
        return keywordDao.getAll().firstOrNull { rule ->
            rule.categoryId != null && rule.id != excludingRuleId &&
                CategoryKeywordNormalizer.normalize(rule.keyword) == normalized
        }
    }

    private suspend fun requireActiveCategory(categoryId: Long): CategoryEntity {
        return dao.getById(categoryId) ?: throw CategoryNotFoundException(categoryId)
    }

    private fun requirePaletteColor(colorKey: String) {
        if (CategoryColorKey.fromStorage(colorKey) == null) {
            throw InvalidCategoryColorKeyException(colorKey)
        }
    }
}
