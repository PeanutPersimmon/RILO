package io.github.peanutpersimmon.rilo.ui.navigation

sealed interface AppRoute {
    data object Timer : AppRoute
    data class EventEditor(val id: Long? = null) : AppRoute
    data class SessionEditor(val id: Long? = null) : AppRoute
}
