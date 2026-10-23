package io.github.peanutpersimmon.rilo.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("""
        SELECT name FROM (
            SELECT TRIM(location) AS name, updatedAtMillis AS usedAt FROM events
            WHERE location IS NOT NULL AND TRIM(location) != ''
            UNION ALL
            SELECT TRIM(location) AS name, updatedAtMillis AS usedAt FROM sessions
            WHERE location IS NOT NULL AND TRIM(location) != ''
        ) GROUP BY name ORDER BY MAX(usedAt) DESC, name ASC LIMIT 5
    """)
    suspend fun recentLocations(): List<String>

    @Query("SELECT * FROM events ORDER BY dateEpochDay, startMinuteOfDay, id")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("""SELECT * FROM events WHERE dateEpochDay <= :lastEpochDay
        AND COALESCE(endDateEpochDay, dateEpochDay) >= :firstEpochDay
        ORDER BY dateEpochDay, startMinuteOfDay, id""")
    suspend fun getScheduledInRange(firstEpochDay: Long, lastEpochDay: Long): List<EventEntity>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long): Int

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events ORDER BY id")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE categoryId = :categoryId")
    suspend fun countByCategoryId(categoryId: Long): Int

    @Query("SELECT * FROM events WHERE id IN (:ids) ORDER BY id")
    suspend fun getByIds(ids: Set<Long>): List<EventEntity>

    @Query("""SELECT * FROM events WHERE dateEpochDay <= :lastEpochDay
        AND COALESCE(endDateEpochDay, dateEpochDay) >= :firstEpochDay
        ORDER BY dateEpochDay, startMinuteOfDay, id""")
    suspend fun getProjectionCandidates(firstEpochDay: Long, lastEpochDay: Long): List<EventEntity>

}

