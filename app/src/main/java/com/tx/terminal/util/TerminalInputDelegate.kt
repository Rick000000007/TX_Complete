package com.tx.terminal.util

import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import com.tx.terminal.data.TerminalSession

class TerminalInputDelegate(private val session: TerminalSession) {
    private var metaState = 0

    companion object {
        const val META_CTRL = 1
        const val META_ALT = 2
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return false
        
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                metaState = metaState or META_CTRL
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                metaState = metaState or META_ALT
                return true
            }
        }

        val output = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> "\r"
            KeyEvent.KEYCODE_DEL -> "\u007F"
            KeyEvent.KEYCODE_TAB -> "\t"
            KeyEvent.KEYCODE_ESCAPE -> "\u001B"
            KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A"
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C"
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D"
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001B[H"
            KeyEvent.KEYCODE_MOVE_END -> "\u001B[F"
            KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~"
            KeyEvent.KEYCODE_PAGEDOWN -> "\u001B[6~"
            else -> {
                if ((metaState and META_CTRL) != 0 && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
                    ((keyCode - KeyEvent.KEYCODE_A + 1)).toChar().toString()
                } else {
                    event.unicodeChar.takeIf { it != 0 }?.let { String(Character.toChars(it)) }
                }
            }
        } ?: return false

        session.writeInput(output.toByteArray(Charsets.UTF_8))
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                metaState = metaState and META_CTRL.inv()
                return true
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                metaState = metaState and META_ALT.inv()
                return true
            }
        }
        return false
    }

    fun createInputConnection(): InputConnection {
        return object : BaseInputConnection(null, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.let {
                    session.writeInput(it.toString().toByteArray(Charsets.UTF_8))
                }
                return true
            }
        }
    }
}

