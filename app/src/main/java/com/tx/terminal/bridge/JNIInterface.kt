package com.tx.terminal.bridge

class JNIInterface {
    init {
        System.loadLibrary("txcore")
    }
    
    external fun createEngine(rows: Int, cols: Int, scrollback: Int): Long
    external fun destroyEngine(handle: Long)
    external fun startShell(handle: Long, shellPath: String): Boolean
    external fun resize(handle: Long, rows: Int, cols: Int)
    external fun writeInput(handle: Long, data: ByteArray)
    external fun setUpdateCallback(handle: Long, callback: NativeUpdateCallback?)
    
    interface NativeUpdateCallback {
        fun onUpdate(damage: NativeDamage)
    }
    
    data class NativeDamage(
        val startRow: Int,
        val endRow: Int,
        val dirty: Boolean,
        val cursorMoved: Boolean,
        val scroll: Boolean
    )
}

