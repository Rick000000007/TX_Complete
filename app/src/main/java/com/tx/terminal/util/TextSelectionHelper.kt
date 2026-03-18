package com.tx.terminal.util

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

data class Selection(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
) {
    fun contains(row: Int, col: Int): Boolean {
        val (topRow, topCol, botRow, botCol) = normalized()
        if (row < topRow || row > botRow) return false
        if (row == topRow && col < topCol) return false
        if (row == botRow && col > botCol) return false
        return true
    }

    fun normalized(): Selection {
        return if (startRow < endRow || (startRow == endRow && startCol <= endCol)) {
            this
        } else {
            Selection(endRow, endCol, startRow, startCol)
        }
    }

    fun isEmpty(): Boolean = startRow == endRow && startCol == endCol
}

class TextSelectionHelper {
    private var selection: Selection? = null
    private var selecting = false

    fun startSelection(row: Int, col: Int) {
        selection = Selection(row, col, row, col)
        selecting = true
    }

    fun updateSelection(row: Int, col: Int) {
        if (!selecting) return
        selection = selection?.let {
            it.copy(endRow = row, endCol = col)
        }
    }

    fun endSelection(): Selection? {
        selecting = false
        return selection?.takeIf { !it.isEmpty() }
    }

    fun clear() {
        selection = null
        selecting = false
    }

    fun getSelection(): Selection? = selection
    fun isSelecting(): Boolean = selecting
}

