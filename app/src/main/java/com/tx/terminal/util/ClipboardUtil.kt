package com.tx.terminal.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardUtil(context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyText(text: String) {
        val clip = ClipData.newPlainText("terminal", text)
        clipboard.setPrimaryClip(clip)
    }

    fun getText(): String? {
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    }

    fun hasText(): Boolean = clipboard.hasPrimaryClip()
}

