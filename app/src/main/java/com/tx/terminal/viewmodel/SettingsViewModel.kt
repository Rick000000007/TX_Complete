package com.tx.terminal.viewmodel

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TerminalSettings(
    val fontSize: Int = 14,
    val fontFamily: String = "JetBrains Mono",
    val foregroundColor: Int = Color.WHITE,
    val backgroundColor: Int = Color.BLACK,
    val cursorColor: Int = Color.GREEN,
    val scrollbackLines: Int = 10000,
    val initialCommand: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _settings = MutableStateFlow(TerminalSettings())
    val settings: StateFlow<TerminalSettings> = _settings

    fun updateFontSize(size: Int) {
        _settings.value = _settings.value.copy(fontSize = size)
    }

    fun updateFontFamily(family: String) {
        _settings.value = _settings.value.copy(fontFamily = family)
    }

    fun updateForegroundColor(color: Int) {
        _settings.value = _settings.value.copy(foregroundColor = color)
    }

    fun updateBackgroundColor(color: Int) {
        _settings.value = _settings.value.copy(backgroundColor = color)
    }

    fun updateScrollback(lines: Int) {
        _settings.value = _settings.value.copy(scrollbackLines = lines)
    }
}

