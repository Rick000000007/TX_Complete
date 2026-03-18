package com.tx.terminal.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

data class ExtraKey(val label: String, val code: String, val width: Float = 1f)

@Composable
fun ExtraKeysBar(
    onKeyPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val standardKeys = listOf(
        ExtraKey("ESC", "\u001B"),
        ExtraKey("TAB", "\t"),
        ExtraKey("HOME", "\u001B[H"),
        ExtraKey("END", "\u001B[F"),
        ExtraKey("PGUP", "\u001B[5~"),
        ExtraKey("PGDN", "\u001B[6~")
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModifierKey(
                label = "CTRL",
                active = ctrlActive,
                onClick = { ctrlActive = !ctrlActive }
            )
            ModifierKey(
                label = "ALT",
                active = altActive,
                onClick = { altActive = !altActive }
            )
            
            standardKeys.forEach { key ->
                KeyButton(
                    label = key.label,
                    onClick = {
                        val output = buildString {
                            if (altActive) append("\u001B")
                            if (ctrlActive && key.code.length == 1) {
                                append((key.code[0].code and 0x1F).toChar())
                            } else {
                                append(key.code)
                            }
                        }
                        onKeyPressed(output)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (!key.isModifier) {
                            ctrlActive = false
                            altActive = false
                        }
                    }
                )
            }
            
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(
                        ExtraKey("↑", "\u001B[A"),
                        ExtraKey("↓", "\u001B[B"),
                        ExtraKey("←", "\u001B[D"),
                        ExtraKey("→", "\u001B[C")
                    ).forEach { KeyButton(it.label) { onKeyPressed(it.code) } }
                    
                    for (i in 1..6) {
                        FKeyButton(i) { n ->
                            val code = when (n) {
                                1 -> "\u001BOP"
                                2 -> "\u001BOQ"
                                3 -> "\u001BOR"
                                4 -> "\u001BOS"
                                else -> "\u001B[${n + 10}~"
                            }
                            onKeyPressed(code)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.padding(2.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModifierKey(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (active) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FKeyButton(n: Int, onClick: (Int) -> Unit) {
    FilledTonalButton(
        onClick = { onClick(n) },
        modifier = Modifier.padding(2.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text("F$n", style = MaterialTheme.typography.labelSmall)
    }
}

