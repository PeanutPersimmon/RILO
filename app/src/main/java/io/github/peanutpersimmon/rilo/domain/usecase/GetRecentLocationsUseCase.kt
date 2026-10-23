package io.github.peanutpersimmon.rilo.domain.usecase

/** Reads existing Event/Session strings; no separate history persistence. */
fun interface LocationHistoryRepository {
    suspend fun recentLocations(): List<String>
}
class GetRecentLocationsUseCase(private val repository: LocationHistoryRepository) {
    suspend operator fun invoke(): List<String> = repository.recentLocations()
}

