package com.tx.terminal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tx.terminal.data.SessionRepository
import com.tx.terminal.data.TerminalSession
import com.tx.terminal.data.TerminalSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {
    
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions = _sessions.asStateFlow()
    
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()
    
    val activeSession: StateFlow<TerminalSession?> = combine(
        _sessions,
        _activeSessionId
    ) { sessions, activeId ->
        sessions.find { it.id == activeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    
    init {
        // Create initial session
        createSession()
    }
    
    fun createSession(): String {
        val session = sessionRepository.createSession()
        _sessions.value += session
        _activeSessionId.value = session.id
        session.start()
        return session.id
    }
    
    fun closeSession(id: String) {
        val session = _sessions.value.find { it.id == id } ?: return
        session.destroy()
        _sessions.value = _sessions.value.filter { it.id != id }
        
        if (_activeSessionId.value == id) {
            _activeSessionId.value = _sessions.value.lastOrNull()?.id
        }
    }
    
    fun switchSession(id: String) {
        if (_sessions.value.any { it.id == id }) {
            _activeSessionId.value = id
        }
    }
    
    fun sendInput(text: String) {
        activeSession.value?.writeInput(text)
    }
    
    fun resizeCurrentSession(rows: Int, cols: Int) {
        activeSession.value?.resize(rows, cols)
    }
    
    override fun onCleared() {
        super.onCleared()
        _sessions.value.forEach { it.destroy() }
    }
}

