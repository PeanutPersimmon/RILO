package io.github.peanutpersimmon.rilo.data.repository

import io.github.peanutpersimmon.rilo.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rilo.domain.usecase.LocationHistoryRepository

class RoomLocationHistoryRepository(private val database: RidoDatabase) : LocationHistoryRepository {
    override suspend fun recentLocations(): List<String> = database.eventDao().recentLocations()
}

