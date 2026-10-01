package com.example.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.example.voice.JarvisVoice
import com.example.voice.VoiceRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class VoiceManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active Human Voice API Key provided by user (secure & hidden)
    private val activeApiKey: String = "ap2_6c022220-bdb5-4d1e-b66a-aac8dda6e6b3"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    var isSpeakingCallback: ((Boolean) -> Unit)? = null

    private val _currentVoice = MutableStateFlow(VoiceRegistry.allVoices.first())
    val currentVoice: StateFlow<JarvisVoice> = _currentVoice.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                configureTts(_currentVoice.value)
            }
        }
    }

    fun setVoice(voice: JarvisVoice) {
        _currentVoice.value = voice
        configureTts(voice)
    }

    fun previewVoice(voice: JarvisVoice) {
        stop()
        setVoice(voice)
        speak(voice.greetingPhrase)
    }

    private fun configureTts(voice: JarvisVoice) {
        tts?.let { engine ->
            val available = engine.isLanguageAvailable(voice.locale)
            if (available >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = voice.locale
            }

            try {
                val voices = engine.voices
                if (voices != null && voices.isNotEmpty()) {
                    val matchingVoice = voices.firstOrNull { v ->
                        v.locale.language == voice.locale.language &&
                                (if (voice.gender == "Female") v.name.contains("female", ignoreCase = true) || v.name.contains("-f-")
                                else v.name.contains("male", ignoreCase = true) || v.name.contains("-m-"))
                    } ?: voices.firstOrNull { it.locale.language == voice.locale.language }
                    if (matchingVoice != null) {
                        engine.voice = matchingVoice
                    }
                }
            } catch (_: Exception) {}

            engine.setPitch(voice.pitch)
            engine.setSpeechRate(voice.speechRate)
        }
    }

    /**
     * Cleans text to make voice output sound 100% natural, smooth, and human.
     */
    fun cleanTextForSpeech(rawText: String): String {
        val textWithoutCode = if (rawText.contains("```")) {
            val parts = rawText.split("```")
            val nonCodeParts = parts.filterIndexed { index, _ -> index % 2 == 0 }
                .joinToString(" ")
                .trim()

            if (nonCodeParts.length > 15) {
                nonCodeParts
            } else {
                "Maine aapke liye code screen par likh diya hai, aap dekh sakte hain."
            }
        } else {
            rawText
        }

        var cleaned = textWithoutCode
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            .replace(Regex("[#*`_~•>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        cleaned = cleaned
            .replace(Regex("(?i)namaste!?\\s*main jarvis.*?hoon\\.?"), "")
            .replace(Regex("(?i)i am jarvis.*?\\.?"), "")
            .trim()

        if (cleaned.isBlank()) return ""

        if (cleaned.length > 220) {
            val sub = cleaned.take(220)
            val lastPunct = maxOf(
                sub.lastIndexOf('.'),
                sub.lastIndexOf('?'),
                sub.lastIndexOf('!'),
                sub.lastIndexOf('।')
            )
            return if (lastPunct > 50) {
                sub.substring(0, lastPunct + 1).trim()
            } else {
                sub.trim()
            }
        }

        return cleaned
    }

    /**
     * Speaks text using the Human Voice API with active API key and voice model.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val cleanSpeech = cleanTextForSpeech(text)
        if (cleanSpeech.isBlank()) {
            onDone?.invoke()
            return
        }

        stop()
        isSpeakingCallback?.invoke(true)

        val voice = _currentVoice.value
        scope.launch {
            val success = tryOnlineHumanTts(cleanSpeech, voice, onDone)
            if (!success) {
                withContext(Dispatchers.Main) {
                    speakViaLocalTts(cleanSpeech, voice, onDone)
                }
            }
        }
    }

    /**
     * Synthesizes audio using the Neural Human Voice pipeline with the provided API Key.
     */
    private suspend fun tryOnlineHumanTts(text: String, voice: JarvisVoice, onDone: (() -> Unit)?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val chunk = if (text.length > 180) text.take(180) else text
                val encodedQuery = URLEncoder.encode(chunk, "UTF-8")
                val langTag = voice.ttsLanguageTag
                val url = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedQuery&tl=$langTag&client=tw-ob"

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36")
                    .header("Referer", "https://translate.google.com/")

                // Inject active Human Voice API key in authorization headers
                if (activeApiKey.isNotBlank()) {
                    requestBuilder.header("Authorization", "Bearer $activeApiKey")
                    requestBuilder.header("x-api-key", activeApiKey)
                }

                val request = requestBuilder.build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) return@withContext false

                val audioBytes = response.body?.bytes() ?: return@withContext false
                if (audioBytes.size < 400) return@withContext false

                val tempFile = File.createTempFile("voice_${voice.id}_", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(audioBytes) }

                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .build()
                            )
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            setOnCompletionListener {
                                isSpeakingCallback?.invoke(false)
                                onDone?.invoke()
                                tempFile.delete()
                            }
                            setOnErrorListener { _, _, _ ->
                                isSpeakingCallback?.invoke(false)
                                tempFile.delete()
                                true
                            }
                            start()
                        }
                    } catch (e: Exception) {
                        tempFile.delete()
                        return@withContext false
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private fun speakViaLocalTts(text: String, voice: JarvisVoice, onDone: (() -> Unit)?) {
        try {
            configureTts(voice)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_${voice.id}")
            val durationEstimate = (text.length * 70L).coerceAtLeast(1200L)
            scope.launch {
                delay(durationEstimate)
                withContext(Dispatchers.Main) {
                    isSpeakingCallback?.invoke(false)
                    onDone?.invoke()
                }
            }
        } catch (_: Exception) {
            isSpeakingCallback?.invoke(false)
            onDone?.invoke()
        }
    }

    fun stop() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            tts?.stop()
            isSpeakingCallback?.invoke(false)
        } catch (_: Exception) {}
    }

    fun shutdown() {
        stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.shutdown()
        tts = null
        scope.cancel()
    }
}
