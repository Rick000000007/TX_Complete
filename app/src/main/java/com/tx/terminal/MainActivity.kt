package com.tx.terminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.tx.terminal.ui.TerminalScreen
import com.tx.terminal.ui.theme.TXTheme
import com.tx.terminal.viewmodel.TerminalViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            TXTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TerminalViewModel = hiltViewModel()
                    TerminalScreen(viewModel = viewModel)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup will be handled by ViewModel
    }
}

