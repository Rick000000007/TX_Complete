package com.tx.terminal.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tx.terminal.viewmodel.TerminalViewModel

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    
    Column(modifier = modifier.fillMaxSize()) {
        // Tab Bar
        TabBar(
            sessions = sessions,
            activeSessionId = activeSession?.id,
            onSessionSelected = viewModel::switchSession,
            onSessionClosed = viewModel::closeSession,
            onCreateSession = viewModel::createSession,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Terminal Content
        Box(modifier = Modifier.weight(1f)) {
            activeSession?.let { session ->
                TerminalSurface(
                    session = session,
                    modifier = Modifier.fillMaxSize()
                )
            } ?: run {
                // Empty state
                Text(
                    text = "No active session",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Extra Keys Bar
        ExtraKeysBar(
            onKeyPressed = { key ->
                viewModel.sendInput(key)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ExtraKeysBar(
    onKeyPressed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        "ESC" to "\u001B",
        "TAB" to "\t",
        "CTRL" to "", // Modifier
        "ALT" to "", // Modifier
        "HOME" to "\u001B[H",
        "END" to "\u001B[F",
        "PGUP" to "\u001B[5~",
        "PGDN" to "\u001B[6~"
    )
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            keys.forEach { (label, seq) ->
                TextButton(
                    onClick = { onKeyPressed(seq) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

