package io.github.peanutpersimmon.rilo.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.peanutpersimmon.rilo.domain.model.SessionKind
import io.github.peanutpersimmon.rilo.domain.model.SessionTemporalType

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(entity = EventEntity::class, parentColumns = ["id"],
            childColumns = ["sourceEventId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("startAtMillis"),
        Index("endAtMillis"),
        Index("sourceEventId"),
        Index("categoryId"),
        Index("kind"),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val kind: SessionKind,
    val temporalType: SessionTemporalType,
    val startAtMillis: Long,
    val endAtMillis: Long? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceEventId: Long? = null,
)

