package com.tx.terminal.ui

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import com.tx.terminal.data.TerminalSession
import com.tx.terminal.util.TextSelectionHelper
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TerminalSurface(
    session: TerminalSession,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    fontFamily: FontFamily = FontFamily.Monospace
) {
    val density = LocalDensity.current
    val clipboardManager = LocalClipboardManager.current
    
    // Measure font metrics
    val textMeasurer = rememberTextMeasurer()
    val sampleText = remember { "M" }
    val textLayoutResult = remember(sampleText, fontSize, fontFamily) {
        textMeasurer.measure(
            text = sampleText,
            style = TextStyle(fontSize = fontSize, fontFamily = fontFamily)
        )
    }
    
    val cellWidth = with(density) { textLayoutResult.size.width.toDp() }
    val cellHeight = with(density) { textLayoutResult.size.height.toDp() }
    
    var size by remember { mutableStateOf(IntSize.Zero) }
    var rows by remember { mutableStateOf(24) }
    var cols by remember { mutableStateOf(80) }
    
    // Update session size when view size changes
    LaunchedEffect(size, cellWidth, cellHeight) {
        if (size.width > 0 && size.height > 0 && cellWidth > 0.dp && cellHeight > 0.dp) {
            val newCols = (size.width / cellWidth.toPx()).toInt()
            val newRows = (size.height / cellHeight.toPx()).toInt()
            if (newCols != cols || newRows != rows) {
                rows = max(1, newCols)
                cols = max(1, newRows)
                session.resize(rows, cols)
            }
        }
    }
    
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    
    // Selection state
    var selection by remember { mutableStateOf<TextSelectionHelper.Selection?>(null) }
    var isSelecting by remember { mutableStateOf(false) }
    
    // Cursor blink
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    
    // Collect screen updates for diff-based rendering
    val damageRegions = remember { mutableStateListOf<TerminalSession.DamageRegion>() }
    LaunchedEffect(session) {
        session.screenUpdates.collectLatest { update ->
            update.damageRegion?.let { damage ->
                damageRegions.add(damage)
                // Invalidate specific region instead of full redraw
            }
        }
    }
    
    val dragObserver = remember {
        object : DragObserver {
            override fun onStart(dragStart: PointerInputChange) {
                isSelecting = true
                val row = (dragStart.position.y / cellHeight.toPx()).toInt()
                val col = (dragStart.position.x / cellWidth.toPx()).toInt()
                selection = TextSelectionHelper.Selection(
                    startRow = row, startCol = col,
                    endRow = row, endCol = col
                )
            }
            
            override fun onDrag(dragEvent: PointerInputChange) {
                val row = (dragEvent.position.y / cellHeight.toPx()).toInt()
                val col = (dragEvent.position.x / cellWidth.toPx()).toInt()
                selection = selection?.copy(
                    endRow = row.coerceIn(0, rows - 1),
                    endCol = col.coerceIn(0, cols - 1)
                )
            }
            
            override fun onStop() {
                isSelecting = false
                // Copy to clipboard if text selected
                selection?.let { sel ->
                    val text = extractSelectedText(sel, session)
                    clipboardManager.setText(AnnotatedString(text))
                }
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusRequester.requestFocus()
                            // Handle tap for cursor positioning or link opening
                        },
                        onDoubleTap = {
                            // Select word
                        },
                        onLongPress = {
                            // Show context menu
                        }
                    )
                }
                .pointerInput(dragObserver) {
                    detectDragGestures(
                        onDragStart = { dragObserver.onStart(it) },
                        onDrag = { _, dragAmount -> 
                            // Update drag
                        },
                        onDragEnd = { dragObserver.onStop() }
                    )
                }
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        handleKeyEvent(keyEvent, session)
                        true
                    } else {
                        false
                    }
                }
        ) {
            size = IntSize(
                this.size.width.toInt(),
                this.size.height.toInt()
            )
            
            // Render terminal content
            drawTerminalContent(
                session = session,
                textMeasurer = textMeasurer,
                cellWidth = cellWidth.toPx(),
                cellHeight = cellHeight.toPx(),
                fontSize = fontSize,
                fontFamily = fontFamily,
                cursorVisible = cursorVisible && isFocused,
                selection = selection
            )
        }
    }
    
    // Initial focus
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private fun DrawScope.drawTerminalContent(
    session: TerminalSession,
    textMeasurer: TextMeasurer,
    cellWidth: Float,
    cellHeight: Float,
    fontSize: TextUnit,
    fontFamily: FontFamily,
    cursorVisible: Boolean,
    selection: TextSelectionHelper.Selection?
) {
    // Background
    drawRect(Color.Black)
    
    // Render cells (optimized: only damaged cells)
    // This is a simplified version - production would use buffered bitmaps
    
    val rows = 24 // Get from actual screen buffer
    val cols = 80
    
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val x = col * cellWidth
            val y = row * cellHeight
            
            // Check if in selection
            val isSelected = selection?.contains(row, col) ?: false
            
            // Background color
            val bgColor = if (isSelected) Color(0xFF0033AA) else Color.Black
            drawRect(
                color = bgColor,
                topLeft = Offset(x, y),
                size = Size(cellWidth, cellHeight)
            )
            
            // Text rendering (simplified - would fetch from native buffer)
            val char = 'X' // Placeholder
            val text = char.toString()
            
            drawText(
                textMeasurer = textMeasurer,
                text = text,
                style = TextStyle(
                    color = if (isSelected) Color.White else Color.Green,
                    fontSize = fontSize,
                    fontFamily = fontFamily
                ),
                topLeft = Offset(x, y)
            )
        }
    }
    
    // Draw cursor
    if (cursorVisible) {
        // Get cursor position from session
        val cursorRow = 0
        val cursorCol = 0
        drawRect(
            color = Color(0xFF00FF00),
            topLeft = Offset(cursorCol * cellWidth, cursorRow * cellHeight),
            size = Size(cellWidth, cellHeight),
            alpha = 0.5f
        )
    }
}

private fun handleKeyEvent(event: KeyEvent, session: TerminalSession): Boolean {
    val keyCode = event.nativeKeyEvent?.keyCode ?: return false
    
    // Convert Android key events to terminal escape sequences
    val input = when (keyCode) {
        android.view.KeyEvent.KEYCODE_ENTER -> "\r"
        android.view.KeyEvent.KEYCODE_DEL -> "\u007F" // DEL
        android.view.KeyEvent.KEYCODE_TAB -> "\t"
        android.view.KeyEvent.KEYCODE_ESCAPE -> "\u001B"
        android.view.KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A"
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B"
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C"
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D"
        else -> {
            event.nativeKeyEvent?.unicodeChar?.toChar()?.toString() ?: return false
        }
    }
    
    session.writeInput(input)
    return true
}

private fun extractSelectedText(
    selection: TextSelectionHelper.Selection,
    session: TerminalSession
): String {
    // Fetch text from session buffer
    return "" // Implement based on native buffer access
}

private operator fun TerminalSession.DamageRegion?.contains(row: Int, col: Int): Boolean {
    // Selection containment logic
    return false
}

