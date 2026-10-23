package io.github.peanutpersimmon.rilo.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("dateEpochDay"),
        Index("categoryId"),
    ],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val startReminderOffsetMinutes: Int? = null,
    val endDateEpochDay: Long? = null,
    val endMinuteOfDay: Int? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

