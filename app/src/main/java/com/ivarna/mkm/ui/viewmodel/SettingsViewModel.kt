package com.ivarna.mkm.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme {
    SYSTEM, DYNAMIC, LIGHT, DARK, AMOLED, NORD, NORD_LIGHT, DRACULA, MONOKAI, GRUVBOX, GRUVBOX_LIGHT, SOLARIZED, SOLARIZED_LIGHT, SYNTHWAVE, ONE_LIGHT
}

class SettingsViewModel : ViewModel() {
    private val _theme = MutableStateFlow(AppTheme.SYSTEM)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
    }
}
