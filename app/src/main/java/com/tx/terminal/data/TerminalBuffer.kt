package com.tx.terminal.data

import android.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe circular buffer for terminal content with optimized diff tracking
 * Supports scrollback and efficient row-based updates
 */
class TerminalBuffer(
    initialRows: Int,
    initialCols: Int,
    val maxScrollback: Int = 10000
) {
    data class Cell(
        val codepoint: Int = ' '.code,
        val fgColor: Int = Color.WHITE,
        val bgColor: Int = Color.BLACK,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val blink: Boolean = false,
        val reverse: Boolean = false,
        val invisible: Boolean = false,
        val width: Int = 1
    )

    data class Row(
        val cells: Array<Cell>,
        var isDirty: Boolean = true,
        var timestamp: Long = System.nanoTime()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Row) return false
            return cells.contentEquals(other.cells) && isDirty == other.isDirty
        }
        override fun hashCode(): Int = cells.contentHashCode() * 31 + isDirty.hashCode()
    }

    data class DamageRegion(
        val startRow: Int,
        val endRow: Int,
        val scroll: Boolean = false,
        val fullRedraw: Boolean = false
    )

    private val lock = ReentrantReadWriteLock()
    private var rows = initialRows
    private var cols = initialCols
    private val activeBuffer = Array(rows) { Row(Array(cols) { Cell() }) }
    private val scrollbackQueue = ArrayDeque<Row>(maxScrollback)
    private var scrollbackStart = 0
    private var scrollbackSize = 0
    private val dirtyRegions = mutableListOf<DamageRegion>()

    fun resize(newRows: Int, newCols: Int): Boolean = lock.write {
        if (newRows == rows && newCols == cols) return true
        
        if (newRows < rows) {
            for (i in newRows until rows) {
                pushToScrollback(activeBuffer[i])
            }
        }

        val newBuffer = Array(newRows) { r ->
            if (r < rows) {
                val newCells = Array(newCols) { c -> 
                    if (c < cols) activeBuffer[r].cells[c] else Cell() 
                }
                Row(newCells, isDirty = true)
            } else {
                Row(Array(newCols) { Cell() }, isDirty = true)
            }
        }
        
        for (i in activeBuffer.indices) {
            if (i < newBuffer.size) activeBuffer[i] = newBuffer[i]
        }
        
        rows = newRows
        cols = newCols
        markDirty(0, rows - 1, fullRedraw = true)
        true
    }

    fun getSize(): Pair<Int, Int> = lock.read { rows to cols }
    
    fun getRow(row: Int): Row? = lock.read { 
        if (row in 0 until rows) activeBuffer[row] else null 
    }

    fun getCell(row: Int, col: Int): Cell? = lock.read {
        if (row in 0 until rows && col in 0 until cols) activeBuffer[row].cells[col] else null
    }

    fun setCell(row: Int, col: Int, cell: Cell) = lock.write {
        if (row in 0 until rows && col in 0 until cols) {
            activeBuffer[row].cells[col] = cell
            activeBuffer[row].isDirty = true
            markDirty(row, row)
        }
    }

    fun markDirty(startRow: Int, endRow: Int, scroll: Boolean = false, fullRedraw: Boolean = false) {
        synchronized(dirtyRegions) {
            dirtyRegions.add(DamageRegion(startRow, endRow, scroll, fullRedraw))
        }
    }

    fun getAndClearDirtyRegions(): List<DamageRegion> = synchronized(dirtyRegions) {
        val regions = dirtyRegions.toList()
        dirtyRegions.clear()
        regions
    }

    fun invalidateAll() = lock.write {
        activeBuffer.forEach { it.isDirty = true }
        markDirty(0, rows - 1, fullRedraw = true)
    }

    fun clear() = lock.write {
        activeBuffer.forEach { row ->
            row.cells.fill(Cell())
            row.isDirty = true
        }
        markDirty(0, rows - 1)
    }

    fun scrollUp(lines: Int = 1, regionTop: Int = 0, regionBottom: Int = rows - 1) = lock.write {
        if (lines <= 0 || regionTop >= regionBottom) return@write
        repeat(lines) {
            pushToScrollback(activeBuffer[regionTop])
            for (i in regionTop until regionBottom) {
                activeBuffer[i] = activeBuffer[i + 1]
                activeBuffer[i].isDirty = true
            }
            activeBuffer[regionBottom] = Row(Array(cols) { Cell() }, isDirty = true)
        }
        markDirty(regionTop, regionBottom, scroll = true)
    }

    fun scrollDown(lines: Int = 1, regionTop: Int = 0, regionBottom: Int = rows - 1) = lock.write {
        if (lines <= 0 || regionTop >= regionBottom) return@write
        repeat(lines) {
            for (i in regionBottom downTo regionTop + 1) {
                activeBuffer[i] = activeBuffer[i - 1]
                activeBuffer[i].isDirty = true
            }
            activeBuffer[regionTop] = Row(Array(cols) { Cell() }, isDirty = true)
        }
        markDirty(regionTop, regionBottom, scroll = true)
    }

    private fun pushToScrollback(row: Row) {
        if (maxScrollback <= 0) return
        if (scrollbackQueue.size < maxScrollback) {
            scrollbackQueue.add(row.copy(cells = row.cells.copyOf()))
        } else {
            scrollbackQueue[scrollbackStart] = row.copy(cells = row.cells.copyOf())
            scrollbackStart = (scrollbackStart + 1) % maxScrollback
        }
        if (scrollbackSize < maxScrollback) scrollbackSize++
    }

    fun getScrollbackLines(count: Int): List<Row> = lock.read {
        if (scrollbackSize == 0) return@read emptyList()
        val result = mutableListOf<Row>()
        val start = (scrollbackStart - minOf(count, scrollbackSize) + maxScrollback) % maxScrollback
        for (i in 0 until minOf(count, scrollbackSize)) {
            val idx = (start + i) % maxScrollback
            if (idx < scrollbackQueue.size) result.add(scrollbackQueue[idx])
        }
        result
    }

    fun clearScrollback() = lock.write {
        scrollbackQueue.clear()
        scrollbackStart = 0
        scrollbackSize = 0
    }

    fun toAnnotatedString(row: Int): AnnotatedString? = lock.read {
        if (row !in 0 until rows) return@read null
        buildAnnotatedString {
            val rowData = activeBuffer[row]
            var currentStyle: SpanStyle? = null
            var spanStart = 0
            
            for ((index, cell) in rowData.cells.withIndex()) {
                val effectiveFg = if (cell.reverse) cell.bgColor else cell.fgColor
                val effectiveBg = if (cell.reverse) cell.fgColor else cell.bgColor
                
                val newStyle = SpanStyle(
                    color = androidx.compose.ui.graphics.Color(effectiveFg),
                    background = androidx.compose.ui.graphics.Color(effectiveBg),
                    fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (cell.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    textDecoration = when {
                        cell.underline -> TextDecoration.Underline
                        cell.blink -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    }
                )
                
                if (currentStyle != newStyle) {
                    if (currentStyle != null) {
                        addStyle(currentStyle, spanStart, index)
                    }
                    currentStyle = newStyle
                    spanStart = index
                }
                append(if (cell.invisible) ' ' else cell.codepoint.toChar())
            }
            currentStyle?.let { addStyle(it, spanStart, rowData.cells.size) }
        }
    }
}

