package com.tx.terminal.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import com.tx.terminal.data.TerminalBuffer

@Immutable
class TerminalTextLayout(
    density: Density,
    fontFamily: FontFamily,
    fontSizePx: Float
) {
    private val textMeasurer = TextMeasurer()
    private val cellWidth: Float
    private val cellHeight: Float
    private val baseline: Float

    init {
        val textLayout = textMeasurer.measure(
            text = "M",
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = androidx.compose.ui.unit.TextUnit(fontSizePx, androidx.compose.ui.unit.TextUnitType.Sp)
            )
        )
        cellWidth = textLayout.size.width.toFloat()
        cellHeight = textLayout.size.height.toFloat()
        baseline = textLayout.firstBaseline
    }

    fun getCellWidth(): Float = cellWidth
    fun getCellHeight(): Float = cellHeight

    fun getPositionForCell(row: Int, col: Int): Offset {
        return Offset(col * cellWidth, row * cellHeight)
    }

    fun getCellFromPosition(x: Float, y: Float): Pair<Int, Int> {
        val col = (x / cellWidth).toInt().coerceAtLeast(0)
        val row = (y / cellHeight).toInt().coerceAtLeast(0)
        return Pair(row, col)
    }

    fun DrawScope.drawCell(cell: TerminalBuffer.Cell, row: Int, col: Int) {
        val x = col * cellWidth
        val y = row * cellHeight

        val bgColor = if (cell.reverse) 
            androidx.compose.ui.graphics.Color(cell.fgColor) 
        else 
            androidx.compose.ui.graphics.Color(cell.bgColor)
        
        drawRect(
            color = bgColor,
            topLeft = Offset(x, y),
            size = Size(cellWidth, cellHeight)
        )

        if (cell.codepoint == 0 || cell.codepoint == ' '.code) return

        val fgColor = if (cell.reverse) 
            androidx.compose.ui.graphics.Color(cell.bgColor) 
        else 
            androidx.compose.ui.graphics.Color(cell.fgColor)
        
        val effectiveColor = if (cell.invisible) bgColor else fgColor

        drawText(
            textMeasurer = textMeasurer,
            text = String(Character.toChars(cell.codepoint)),
            style = TextStyle(
                color = effectiveColor,
                fontFamily = fontFamily,
                fontSize = androidx.compose.ui.unit.TextUnit(cellHeight * 0.7f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
                textDecoration = when {
                    cell.underline -> TextDecoration.Underline
                    cell.blink -> TextDecoration.LineThrough
                    else -> TextDecoration.None
                }
            ),
            topLeft = Offset(x, y + baseline * 0.2f)
        )
    }

    fun DrawScope.drawSelection(
        startRow: Int, startCol: Int, endRow: Int, endCol: Int,
        selectionColor: androidx.compose.ui.graphics.Color
    ) {
        val (topRow, leftCol, botRow, rightCol) = if (startRow < endRow || (startRow == endRow && startCol <= endCol)) {
            Quadruple(startRow, startCol, endRow, endCol)
        } else {
            Quadruple(endRow, endCol, startRow, startCol)
        }

        for (row in topRow..botRow) {
            val colStart = if (row == topRow) leftCol else 0
            val colEnd = if (row == botRow) rightCol else Int.MAX_VALUE
            
            drawRect(
                color = selectionColor,
                topLeft = Offset(colStart * cellWidth, row * cellHeight),
                size = Size((colEnd - colStart + 1) * cellWidth, cellHeight),
                alpha = 0.5f
            )
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

