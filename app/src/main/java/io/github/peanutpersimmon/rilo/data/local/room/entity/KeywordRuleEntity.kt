package io.github.peanutpersimmon.rilo.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keyword_rules",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["isEnabled", "priority"]),
        Index("categoryId"),
    ],
)
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: Long? = null,
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

