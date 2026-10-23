package io.github.peanutpersimmon.rilo.domain.model

sealed interface DomainError {
    data object EmptyTitle : DomainError
    data object InvalidTime : DomainError
    data object SleepIntervalTooLong : DomainError
    data object SessionOverlap : DomainError
    data object NoActiveSession : DomainError
    data object ParentCycle : DomainError
    data object InvalidKeywordRule : DomainError
    data object CommandAlreadyProcessed : DomainError
    data class NotFound(val type: String, val id: Long) : DomainError
    data class DataIntegrity(val reason: String) : DomainError
}

sealed interface DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>
    data class Failure(val error: DomainError) : DomainResult<Nothing>
}

