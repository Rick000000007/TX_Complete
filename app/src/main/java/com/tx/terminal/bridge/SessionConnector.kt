package com.tx.terminal.bridge

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages low-level JNI connections and lifecycle
 * Thread-safe bridge between TerminalSession and native engine
 */
object SessionConnector {
    private val jni = JNIInterface()
    private val activeConnections = ConcurrentHashMap<Long, ConnectionState>()
    private val connectionCounter = java.util.concurrent.atomic.AtomicLong(0)

    data class ConnectionState(
        val handle: Long,
        var callback: TerminalCallback?,
        val createTime: Long = System.currentTimeMillis()
    )

    fun createEngine(rows: Int, cols: Int, scrollback: Int): Long {
        val handle = jni.createEngine(rows, cols, scrollback)
        val state = ConnectionState(handle = handle, callback = null)
        activeConnections[handle] = state
        Log.d("SessionConnector", "Created engine handle: $handle")
        return handle
    }

    fun registerCallback(handle: Long, callback: TerminalCallback) {
        activeConnections[handle]?.let {
            it.callback = callback
            jni.setCallback(handle, callback)
            Log.d("SessionConnector", "Callback registered for handle $handle")
        }
    }

    fun unregisterCallback(handle: Long) {
        activeConnections[handle]?.let {
            jni.setCallback(handle, null)
            it.callback = null
        }
    }

    fun startShell(handle: Long, shellPath: String): Boolean {
        return jni.startShell(handle, shellPath)
    }

    fun writeInput(handle: Long, data: ByteArray) {
        jni.writeInput(handle, data)
    }

    fun resize(handle: Long, rows: Int, cols: Int) {
        jni.resize(handle, rows, cols)
    }

    fun destroyEngine(handle: Long) {
        activeConnections.remove(handle)
        jni.destroyEngine(handle)
    }

    fun getJNI(): JNIInterface = jni

    fun getStats(): String {
        return "Active connections: ${activeConnections.size}"
    }
}

