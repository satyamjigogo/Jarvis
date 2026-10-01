package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.MyApplicationTheme

import androidx.activity.viewModels
import com.example.ui.ChatScreen
import com.example.ui.ChatViewModel
import androidx.compose.runtime.*
import com.example.ui.VoiceManager

class MainActivity : ComponentActivity() {
  private val viewModel: ChatViewModel by viewModels()
  private lateinit var voiceManager: VoiceManager

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    voiceManager = VoiceManager(this)
    enableEdgeToEdge()
    setContent {
      var isDarkTheme by remember { mutableStateOf(true) }
      MyApplicationTheme(darkTheme = isDarkTheme) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          Box(modifier = Modifier.padding(innerPadding)) {
              ChatScreen(viewModel = viewModel, onToggleTheme = { isDarkTheme = !isDarkTheme }, voiceManager = voiceManager)
          }
        }
      }
    }
  }

  override fun onDestroy() {
    voiceManager.shutdown()
    super.onDestroy()
  }
}


