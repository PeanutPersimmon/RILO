package io.github.peanutpersimmon.rilo.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity

@Dao
interface ProcessedCommandDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_commands WHERE commandId = :commandId)")
    suspend fun exists(commandId: String): Boolean

    @Insert
    suspend fun insert(command: ProcessedCommandEntity)

    @Query("""
        SELECT EXISTS(SELECT 1 FROM categories)
            OR EXISTS(SELECT 1 FROM events)
            OR EXISTS(SELECT 1 FROM sessions)
            OR EXISTS(SELECT 1 FROM keyword_rules)
            OR EXISTS(SELECT 1 FROM processed_commands
                      WHERE commandId NOT IN (:createdMarker, :completedMarker))
    """)
    suspend fun hasPriorUserData(createdMarker: String, completedMarker: String): Boolean
}
