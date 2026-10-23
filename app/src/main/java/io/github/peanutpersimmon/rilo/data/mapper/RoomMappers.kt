package io.github.peanutpersimmon.rilo.data.mapper

import io.github.peanutpersimmon.rilo.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rilo.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rilo.domain.model.Category
import io.github.peanutpersimmon.rilo.domain.model.Event
import io.github.peanutpersimmon.rilo.domain.model.KeywordRule
import io.github.peanutpersimmon.rilo.domain.model.ProcessedCommand
import io.github.peanutpersimmon.rilo.domain.model.Session

fun EventEntity.toDomain(): Event = Event(
    id = id,
    title = title,
    note = note,
    location = location,
    dateEpochDay = dateEpochDay,
    startMinuteOfDay = startMinuteOfDay,
    startReminderOffsetMinutes = startReminderOffsetMinutes,
    endDateEpochDay = endDateEpochDay,
    endMinuteOfDay = endMinuteOfDay,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    location = location?.trim()?.takeIf(String::isNotEmpty),
    dateEpochDay = dateEpochDay,
    startMinuteOfDay = startMinuteOfDay,
    startReminderOffsetMinutes = startReminderOffsetMinutes,
    endDateEpochDay = endDateEpochDay,
    endMinuteOfDay = endMinuteOfDay,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    title = title,
    note = note,
    location = location,
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    location = location?.trim()?.takeIf(String::isNotEmpty),
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    // Old backups may contain archived rows. Archive is no longer a product state.
    isArchived = false,
    sortOrder = sortOrder,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    colorKey = colorKey,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name.trim(),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    colorKey = colorKey,
)

fun KeywordRuleEntity.toDomain(): KeywordRule = KeywordRule(
    id = id,
    keyword = keyword,
    categoryId = categoryId,
    isEnabled = isEnabled,
    priority = priority,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun KeywordRule.toEntity(): KeywordRuleEntity = KeywordRuleEntity(
    id = id,
    keyword = keyword.trim(),
    categoryId = categoryId,
    isEnabled = isEnabled,
    priority = priority,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun ProcessedCommandEntity.toDomain(): ProcessedCommand = ProcessedCommand(commandId, processedAtMillis)

fun ProcessedCommand.toEntity(): ProcessedCommandEntity = ProcessedCommandEntity(commandId.trim(), processedAtMillis)

