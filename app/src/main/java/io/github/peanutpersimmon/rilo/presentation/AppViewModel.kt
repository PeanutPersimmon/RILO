package io.github.peanutpersimmon.rilo.presentation

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import io.github.peanutpersimmon.rilo.ui.navigation.RootDestination
import io.github.peanutpersimmon.rilo.ui.navigation.AppRoute
import io.github.peanutpersimmon.rilo.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update

data class AppUiState(
    val destination: RootDestination = RootDestination.Today,
    val themeMode: ThemeMode = ThemeMode.Light,
    val route: AppRoute? = null,
    val returnRoute: AppRoute? = null,
)

class AppViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val preferences = io.github.peanutpersimmon.rilo.data.preferences.AppearancePreferences(application)
    fun setNightMode(dark: Boolean) { viewModelScope.launch { preferences.setDark(dark) } }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    init {
        viewModelScope.launch { preferences.dark.collect { dark -> _uiState.update { it.copy(themeMode = if (dark) ThemeMode.Dark else ThemeMode.Light) } } }
    }


    fun selectDestination(destination: RootDestination) {
        _uiState.update { current -> current.copy(destination = destination, route = null, returnRoute = null) }
    }

    fun openTimer() {
        navigate(AppRoute.Timer)
    }

    fun closeTimer() {
        _uiState.update { it.copy(route = null, returnRoute = null) }
    }

    fun navigate(route: AppRoute) {
        _uiState.update { current ->
            val returnRoute = current.route.takeIf { it == AppRoute.Timer && route is AppRoute.SessionEditor }
            current.copy(route = route, returnRoute = returnRoute)
        }
    }

    fun back() {
        _uiState.update { current -> current.copy(route = current.returnRoute, returnRoute = null) }
    }
}
