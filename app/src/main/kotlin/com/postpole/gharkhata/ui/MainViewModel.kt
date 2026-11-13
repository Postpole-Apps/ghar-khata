package com.postpole.gharkhata.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.analytics.Analytics
import com.postpole.gharkhata.data.SettingsRepository
import com.postpole.gharkhata.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ThemeState(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = false)

/** Activity-level state: theme for [com.postpole.gharkhata.ui.theme.GharKhataTheme] and screen-view logging. */
@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    private val analytics: Analytics,
) : ViewModel() {

    val theme: StateFlow<ThemeState> = combine(settings.themeMode, settings.dynamicColor, ::ThemeState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeState())

    fun onScreen(route: String) {
        analytics.logScreenView(route)
    }
}
