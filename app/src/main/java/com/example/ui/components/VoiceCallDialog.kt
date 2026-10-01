package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceCallDialog(
    voiceManager: VoiceManager,
    onQueryAi: suspend (String) -> String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVoice by voiceManager.currentVoice.collectAsState()

    var callDurationSeconds by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isJarvisSpeaking by remember { mutableStateOf(true) }
    var isListening by remember { mutableStateOf(false) }
    var showVoiceSelector by remember { mutableStateOf(false) }
    var latestSpeechText by remember { mutableStateOf(currentVoice.greetingPhrase) }
    var userSpokenText by remember { mutableStateOf<String?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var quickTextInput by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    // Pulsing circle animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDurationSeconds++
        }
    }

    // Connect voiceManager speaking callback
    DisposableEffect(voiceManager) {
        voiceManager.isSpeakingCallback = { speaking ->
            isJarvisSpeaking = speaking
        }
        onDispose {
            voiceManager.isSpeakingCallback = null
            voiceManager.stop()
        }
    }

    // Greet upon connecting with the active voice persona
    LaunchedEffect(Unit) {
        val greeting = currentVoice.greetingPhrase
        latestSpeechText = greeting
        voiceManager.speak(greeting) {
            isJarvisSpeaking = false
        }
    }

    // Handle user query by sending it to AI and speaking the AI answer back
    fun handleUserQuery(query: String) {
        if (query.isBlank()) return
        userSpokenText = query
        isListening = false
        latestSpeechText = "Aapki aawaz translate ho rahi hai... AI javab tayyar kar raha hai."
        isJarvisSpeaking = true

        scope.launch {
            try {
                val aiResponse = onQueryAi(query)
                latestSpeechText = aiResponse
                voiceManager.speak(aiResponse) {
                    isJarvisSpeaking = false
                }
            } catch (e: Exception) {
                val fallback = "Mujhe aapka sandesh mil gaya: $query. Main aapki poori madad karne ke liye taiyyar hoon."
                latestSpeechText = fallback
                voiceManager.speak(fallback) {
                    isJarvisSpeaking = false
                }
            }
        }
    }

    fun startListeningInternal() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            latestSpeechText = "Microphone recognition is unavailable on this device. Neeche suggestion tap karein ya likh kar bhejein."
            return
        }

        try {
            voiceManager.stop()
            isJarvisSpeaking = false
            speechRecognizer?.destroy()

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS is listening (Bolna shuru karein)...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    latestSpeechText = "🎙️ Sun raha hoon... Kripya boliye."
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    latestSpeechText = "Aawaz process ho rahi hai..."
                }
                override fun onError(error: Int) {
                    isListening = false
                    if (userSpokenText == null) {
                        latestSpeechText = "Aawaz record nahi ho saki. Mic dobara tap karein ya prompt chunein."
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim()
                    if (!spoken.isNullOrBlank()) {
                        handleUserQuery(spoken)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { partial ->
                        userSpokenText = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            latestSpeechText = "Mic start error: ${e.message}"
        }
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningInternal()
        } else {
            latestSpeechText = "Microphone permission ki zaroorat hai. Kripya allow karein."
        }
    }

    fun onMicTapped() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                startListeningInternal()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    Dialog(
        onDismissRequest = {
            voiceManager.stop()
            speechRecognizer?.destroy()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Call Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 18.dp)
                ) {
                    Text(
                        "JARVIS AI Live Voice Call",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isListening) Color(0xFF10B981)
                                        else if (isJarvisSpeaking) Color(0xFF38BDF8)
                                        else Color(0xFF94A3B8)
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isListening) "Listening (Record ho raha hai)... • $formattedTime"
                                else if (isJarvisSpeaking) "${currentVoice.name} Speaking • $formattedTime"
                                else "Connected • $formattedTime",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Voice Persona Selector Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF334155),
                        modifier = Modifier.clickable { showVoiceSelector = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(currentVoice.flagEmoji, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Voice: ${currentVoice.name}",
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change Voice", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Middle: Glowing Animated JARVIS Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(220.dp)
                ) {
                    // Outer pulsating glow
                    Box(
                        modifier = Modifier
                            .size(if (isJarvisSpeaking || isListening) 210.dp else 180.dp)
                            .scale(if (isJarvisSpeaking || isListening) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFF10B981).copy(alpha = 0.25f)
                                else if (isJarvisSpeaking) Color(0xFF38BDF8).copy(alpha = 0.25f)
                                else Color(0xFF64748B).copy(alpha = 0.15f)
                            )
                    )

                    // Inner glowing circle
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isListening) listOf(Color(0xFF34D399), Color(0xFF059669))
                                    else if (isJarvisSpeaking) listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                                    else listOf(Color(0xFF475569), Color(0xFF1E293B))
                                )
                            )
                            .border(3.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isListening) Icons.Default.Mic else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Spoken dialogue transcript card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        userSpokenText?.let { uText ->
                            Text(
                                "You: $uText",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                        Text(
                            latestSpeechText,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Quick Talk Prompt Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Kaise ho?" to "Aap kaise hain? Aaj ka din kaisa raha?",
                        "Ek Joke sunao" to "Ek mazedaar chutkula sunao",
                        "Chai ki recipe" to "Kadak masala chai kaise banate hain?",
                        "Ek Shayari sunao" to "Ek khoobsurat sher ya shayari sunao",
                        "Android App banao" to "Mujhe ek Android app banani hai",
                        "Python Code" to "Python me clean code example batao"
                    ).forEach { (label, query) ->
                        SuggestionChip(
                            onClick = { handleUserQuery(query) },
                            label = { Text(label, color = Color(0xFF38BDF8), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF1E293B))
                        )
                    }
                }

                // Optional manual text input for quick typing inside call
                if (showManualInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = quickTextInput,
                            onValueChange = { quickTextInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type and AI will answer in voice...", fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = {
                            if (quickTextInput.isNotBlank()) {
                                val t = quickTextInput.trim()
                                quickTextInput = ""
                                handleUserQuery(t)
                            }
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF38BDF8))
                        }
                    }
                }

                // Bottom Call Controls: [Mute] [Mic: Tap to Record & Translate] [Type Input] [End Call]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute / Speaker Toggle
                    FilledIconButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) voiceManager.stop()
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF334155)
                        )
                    ) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color(0xFFEF4444) else Color.White
                        )
                    }

                    // Center Mic: Tap Once to Record, Translate & Send to AI
                    FilledIconButton(
                        onClick = { onMicTapped() },
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isListening) Color(0xFF10B981) else Color(0xFF0284C7)
                        )
                    ) {
                        Icon(
                            if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Tap to Record",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Keyboard toggle button
                    FilledIconButton(
                        onClick = { showManualInput = !showManualInput },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showManualInput) Color(0xFF0284C7) else Color(0xFF334155)
                        )
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Type", tint = Color.White)
                    }

                    // End Call Button (Red)
                    FilledIconButton(
                        onClick = {
                            voiceManager.stop()
                            speechRecognizer?.destroy()
                            onDismiss()
                        },
                        modifier = Modifier.size(52.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFDC2626)
                        )
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            if (showVoiceSelector) {
                VoiceSelectorDialog(
                    voiceManager = voiceManager,
                    onDismiss = {
                        showVoiceSelector = false
                        latestSpeechText = currentVoice.greetingPhrase
                        voiceManager.speak(currentVoice.greetingPhrase)
                    }
                )
            }
        }
    }
}
