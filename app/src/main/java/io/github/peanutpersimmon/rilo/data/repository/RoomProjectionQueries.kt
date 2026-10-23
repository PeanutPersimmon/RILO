package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.data.mapper.toDomain
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val PROJECTION_DATA_CHANGE_TABLES = arrayOf("sessions", "events", "categories")

internal class RoomProjectionQueries(private val database: RidoDatabase) {
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()
    private val categoryDao = database.categoryDao()
    private val keywordRuleDao = database.keywordRuleDao()

    fun observeDataChanges(): Flow<Unit> = database.invalidationTracker
        .createFlow(*PROJECTION_DATA_CHANGE_TABLES, emitInitialState = true).map { }
    fun observeActiveSession(): Flow<Session?> = sessionDao.observeActive().map { it?.toDomain() }.distinctUntilChanged()
    suspend fun getNightSleeps(): List<Session> = sessionDao.getNightSleeps().map { it.toDomain() }
    suspend fun getSessions(start: Long, end: Long): List<Session> = sessionDao.getIntersecting(start, end).map { it.toDomain() }
    suspend fun getCompletedActivityIntervalsInRange(start: Long, end: Long): List<Session> =
        sessionDao.getCompletedActivityEntitiesInRange(start, end).map { it.toDomain() }
    suspend fun getActivityFactsInRange(start: Long, end: Long): List<Session> =
        sessionDao.getActivityFactEntitiesInRange(start, end).map { it.toDomain() }
    suspend fun getNapsInRange(start: Long, end: Long): List<Session> = sessionDao.getNapEntitiesInRange(start, end).map { it.toDomain() }
    suspend fun getNightSleepsInRange(start: Long, end: Long): List<Session> =
        sessionDao.getNightSleepEntitiesInRange(start, end).map { it.toDomain() }
    suspend fun getEvents(firstDay: Long, lastDay: Long, start: Long, end: Long): List<Event> =
        eventDao.getProjectionCandidates(firstDay, lastDay).map { it.toDomain() }
    suspend fun getEventsByIds(ids: Set<Long>): List<Event> = eventDao.getByIds(ids).map { it.toDomain() }
    suspend fun getEnabledKeywordRules(): List<KeywordRule> = keywordRuleDao.getEnabledCategoryRules().map { it.toDomain() }
    suspend fun getCategories(): List<Category> = categoryDao.getAllIncludingArchived().map { it.toDomain() }
}
