package com.tx.terminal.data

import android.util.Log
import com.tx.terminal.bridge.SessionConnector
import com.tx.terminal.bridge.TerminalCallback
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

data class TerminalSize(val rows: Int, val cols: Int)

class TerminalSession(
    val id: String,
    private val connector: SessionConnector,
    private val shellPath: String,
    initialSize: TerminalSize,
    private val onOutput: (startRow: Int, endRow: Int, scroll: Boolean, fullRedraw: Boolean) -> Unit,
    private val onExit: (exitCode: Int) -> Unit
) : TerminalCallback {
    private val handle: Long = connector.createEngine(initialSize.rows, initialSize.cols, 10000)
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val _title = MutableStateFlow("Terminal")
    val title: StateFlow<String> = _title.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val destroyed = AtomicBoolean(false)

    init {
        connector.registerCallback(handle, this)
    }

    fun start(): Boolean {
        if (_isRunning.value) return false
        val started = connector.startShell(handle, shellPath)
        if (started) {
            _isRunning.value = true
            scope.launch { monitorSession() }
        }
        return started
    }

    fun writeInput(bytes: ByteArray) {
        if (!_isRunning.value || destroyed.get()) return
        connector.writeInput(handle, bytes)
    }

    fun resize(rows: Int, cols: Int) {
        connector.resize(handle, rows, cols)
    }

    fun destroy() {
        if (destroyed.compareAndSet(false, true)) {
            scope.cancel()
            connector.destroyEngine(handle)
            _isRunning.value = false
        }
    }

    override fun onDamage(startRow: Int, endRow: Int, scroll: Boolean, fullRedraw: Boolean) {
        onOutput(startRow, endRow, scroll, fullRedraw)
    }

    override fun onProcessExit(exitCode: Int) {
        _isRunning.value = false
        onExit(exitCode)
    }

    private suspend fun monitorSession() {
        while (_isRunning.value && !destroyed.get()) {
            delay(1000)
        }
    }
}

