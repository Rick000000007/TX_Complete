package com.tx.terminal.data

import android.util.Log
import com.tx.terminal.bridge.SessionConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("SessionRepository")
    )
    private val sessions = ConcurrentHashMap<String, TerminalSession>()
    
    private val _sessionOutputs = MutableSharedFlow<TerminalOutput>(
        extraBufferCapacity = 256,
        onBufferOverflow = { Log.w("SessionRepository", "Buffer overflow") }
    )
    val sessionOutputs: SharedFlow<TerminalOutput> = _sessionOutputs.asSharedFlow()
    
    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    data class TerminalOutput(
        val sessionId: String,
        val startRow: Int,
        val endRow: Int,
        val scroll: Boolean,
        val fullRedraw: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun createSession(
        shellPath: String = "/system/bin/sh",
        initialSize: TerminalSize = TerminalSize(24, 80)
    ): TerminalSession {
        val id = UUID.randomUUID().toString()
        val session = TerminalSession(
            id = id,
            connector = SessionConnector,
            shellPath = shellPath,
            initialSize = initialSize,
            onOutput = { startRow, endRow, scroll, fullRedraw ->
                scope.launch {
                    _sessionOutputs.emit(
                        TerminalOutput(id, startRow, endRow, scroll, fullRedraw)
                    )
                }
            },
            onExit = { code ->
                Log.i("SessionRepository", "Session $id exited: $code")
                sessions.remove(id)
                if (_activeSessionId.value == id) {
                    _activeSessionId.value = sessions.keys.firstOrNull()
                }
            }
        )
        sessions[id] = session
        if (_activeSessionId.value == null) _activeSessionId.value = id
        return session
    }

    fun getSession(id: String): TerminalSession? = sessions[id]
    fun getActiveSession(): TerminalSession? = sessions[_activeSessionId.value]
    
    fun setActiveSession(id: String) {
        if (sessions.containsKey(id)) _activeSessionId.value = id
    }

    fun closeSession(id: String) {
        sessions[id]?.destroy()
        sessions.remove(id)
        if (_activeSessionId.value == id) {
            _activeSessionId.value = sessions.keys.firstOrNull()
        }
    }

    fun sendInputToActive(text: String) {
        _activeSessionId.value?.let { sessions[it]?.writeInput(text.toByteArray(Charsets.UTF_8)) }
    }

    fun resizeActive(rows: Int, cols: Int) {
        _activeSessionId.value?.let { sessions[it]?.resize(rows, cols) }
    }

    fun clearAll() {
        scope.launch {
            sessions.values.forEach { it.destroy() }
            sessions.clear()
            _activeSessionId.value = null
        }
    }

    fun getSessionCount(): Int = sessions.size
}

