package io.github.peanutpersimmon.rilo.data.local.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "processed_commands")
data class ProcessedCommandEntity(
    @PrimaryKey val commandId: String,
    val processedAtMillis: Long,
)
