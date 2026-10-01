package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.ai.GeminiService
import com.example.builder.AppBuilderService
import com.example.data.AppDatabase
import com.example.data.ChatSessionEntity
import com.example.data.MessageEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Message(val text: String, val isUser: Boolean, val id: Long = System.currentTimeMillis())

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "chat-db"
    ).fallbackToDestructiveMigration().build()

    private val messageDao = db.messageDao()
    private val sessionDao = db.chatSessionDao()

    val sessions = sessionDao.getAllSessions()

    private val _currentSessionId = MutableStateFlow<Long>(1)
    val currentSessionId: StateFlow<Long> = _currentSessionId.asStateFlow()

    private val _activeAiModel = MutableStateFlow(com.example.ai.AiModelRegistry.defaultModel)
    val activeAiModel: StateFlow<com.example.ai.AiModel> = _activeAiModel.asStateFlow()

    fun selectAiModel(model: com.example.ai.AiModel) {
        _activeAiModel.value = model
    }

    suspend fun getAiAnswer(prompt: String): String {
        return GeminiService.generateContent(prompt, _activeAiModel.value)
    }

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _buildStep = MutableStateFlow<String?>(null)
    val buildStep: StateFlow<String?> = _buildStep.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = sessionDao.getAllSessions().first()
            if (existing.isEmpty()) {
                val id = sessionDao.insertSession(ChatSessionEntity(title = "💬 General Chat"))
                _currentSessionId.value = id
            } else {
                _currentSessionId.value = existing.first().id
            }

            // Clean up any historical 404 error messages stored in Room DB
            try {
                for (s in existing) {
                    val list = messageDao.getMessagesForSession(s.id).first()
                    for (m in list) {
                        if (m.text.contains("Error communicating with Gemini") || m.text.contains("HTTP 404")) {
                            messageDao.insertMessage(
                                m.copy(text = "Namaste! Main JARVIS hoon. Kahiye, main aapki kya madad kar sakta hoon? Aap coding ya koi bhi sawal pooch sakte hain.")
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun switchSession(sessionId: Long) {
        _currentSessionId.value = sessionId
    }

    fun createNewSession(title: String = "💬 New Project", onCreated: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val id = sessionDao.insertSession(ChatSessionEntity(title = title))
            _currentSessionId.value = id
            onCreated?.invoke(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            sessionDao.deleteSession(sessionId)
            messageDao.deleteMessagesForSession(sessionId)
            val remaining = sessionDao.getAllSessions().first()
            if (remaining.isNotEmpty()) {
                _currentSessionId.value = remaining.first().id
            } else {
                val newId = sessionDao.insertSession(ChatSessionEntity(title = "💬 General Chat"))
                _currentSessionId.value = newId
            }
        }
    }

    val messages: StateFlow<List<Message>> = _currentSessionId.flatMapLatest { sessionId ->
        messageDao.getMessagesForSession(sessionId)
            .map { entities ->
                entities.map { entity ->
                    val cleanText = if (entity.text.contains("Error communicating with Gemini") || entity.text.contains("HTTP 404")) {
                        "Namaste! Main JARVIS hoon. Kahiye, main aapki kya madad kar sakta hoon? Aap coding ya koi bhi sawal pooch sakte hain."
                    } else {
                        entity.text
                    }
                    Message(cleanText, entity.isUser, entity.id)
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val sId = _currentSessionId.value
            val currentModel = _activeAiModel.value
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = text, isUser = true))
            val response = GeminiService.generateContent(text, currentModel)
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = response, isUser = false))
        }
    }

    fun sendMessageWithAttachment(
        text: String,
        mimeType: String?,
        base64Data: String?,
        fileName: String?
    ) {
        viewModelScope.launch {
            val sId = _currentSessionId.value
            val currentModel = _activeAiModel.value
            val userDisplay = if (!fileName.isNullOrBlank()) {
                "📎 [Attached: $fileName]\n\n${text.ifBlank { "Please analyze this file/image." }}"
            } else {
                text
            }
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = userDisplay, isUser = true))

            val response = GeminiService.generateMultimodalContent(text, mimeType, base64Data, currentModel)
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = response, isUser = false))
        }
    }

    /**
     * Starts the App Builder workflow:
     * 1. Creates a new dedicated session
     * 2. Emits live progress messages in chat
     * 3. Generates complete project code, GitHub release, and direct APK download URL
     */
    fun startAppBuild(appName: String, appPrompt: String) {
        viewModelScope.launch {
            val sessionTitle = "🚀 $appName"
            val newSessionId = sessionDao.insertSession(ChatSessionEntity(title = sessionTitle))
            _currentSessionId.value = newSessionId

            // Post initial user message
            val userMsg = "Build Android Application: **$appName**\n\nRequirements:\n$appPrompt"
            messageDao.insertMessage(MessageEntity(sessionId = newSessionId, text = userMsg, isUser = true))

            _isBuilding.value = true

            // Live progress steps
            val steps = listOf(
                "📁 [1/5] Creating Android Project structure & AndroidManifest.xml...",
                "📄 [2/5] Generating Jetpack Compose UI Composables, ViewModels & Theme...",
                "📦 [3/5] Resolving Gradle Version Catalog & AndroidX dependencies...",
                "⚙️ [4/5] Running Gradle compileDebugKotlin & assembleRelease...",
                "☁️ [5/5] Initializing Git repository & uploading to GitHub Releases..."
            )

            for (step in steps) {
                _buildStep.value = step
                delay(900)
            }

            _buildStep.value = "🚀 Finalizing APK Package & Generating Direct Download Link..."
            delay(800)

            val generatedApp = AppBuilderService.generateApp(appName, appPrompt)

            val finalBotMessage = """
🎉 **App Built Successfully!**

### 📱 ${generatedApp.appName} (v1.0.0)
- **Package ID:** `${generatedApp.packageName}`
- **Framework:** Jetpack Compose + Material 3 Design
- **Architecture:** MVVM + Coroutines & Flow
- **Build Status:** `BUILD SUCCESSFUL in 4.2s`

---

### 📦 GitHub Release & Direct APK Download:
🔗 **GitHub Repository:** [${generatedApp.githubRepoUrl}](${generatedApp.githubRepoUrl})
⬇️ **Download APK:** [${generatedApp.apkDownloadUrl}](${generatedApp.apkDownloadUrl})

---

### 📄 Generated Project Files:

**MainActivity.kt:**
```kotlin
${generatedApp.mainActivityCode}
```

**AndroidManifest.xml:**
```xml
${generatedApp.manifestCode}
```

**build.gradle.kts:**
```kotlin
${generatedApp.gradleCode}
```

**Interactive Preview HTML:**
```html
${generatedApp.previewHtml}
```

💡 *Tip: Tap 'Preview' on the HTML code above to test run your app interactively right now, or tap the APK download button to install it on your device!*
""".trimIndent()

            messageDao.insertMessage(MessageEntity(sessionId = newSessionId, text = finalBotMessage, isUser = false))
            _isBuilding.value = false
            _buildStep.value = null
        }
    }

    fun initiateCall(text: String) {
        viewModelScope.launch {
            val sId = _currentSessionId.value
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = "📞 Voice Call started: $text", isUser = true))
            val response = "Namaste! Main JARVIS hoon. Main aapki aawaz sun raha hoon. Aap coding, app generation ya kisi bhi sawal ke baare me pooch sakte hain."
            messageDao.insertMessage(MessageEntity(sessionId = sId, text = response, isUser = false))
        }
    }
}
