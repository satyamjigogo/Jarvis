package com.example.ai

data class AiModel(
    val id: String,
    val name: String,
    val displayName: String,
    val endpointModel: String,
    val description: String,
    val speedBadge: String,
    val intelligenceBadge: String,
    val iconEmoji: String,
    val systemPrompt: String
)

object AiModelRegistry {
    val models = listOf(
        AiModel(
            id = "gemini_2_5_flash",
            name = "Gemini 2.5 Flash",
            displayName = "⚡ Gemini 2.5 Flash",
            endpointModel = "gemini-flash-latest",
            description = "Fast, natural conversation, answers any topic, coding & analysis.",
            speedBadge = "Ultra Fast",
            intelligenceBadge = "High",
            iconEmoji = "⚡",
            systemPrompt = "You are a helpful, warm, and highly capable AI assistant. Talk naturally, directly, and conversationally. Answer whatever the user asks — everyday talk, general knowledge, advice, stories, jokes, or coding. NEVER say 'I am JARVIS' or introduce yourself repeatedly. If the user writes in Hindi or Hinglish, reply in natural, fluent Hindi/Hinglish."
        ),
        AiModel(
            id = "gemini_3_1_pro",
            name = "Gemini 3.1 Pro",
            displayName = "🧠 Gemini 3.1 Pro",
            endpointModel = "gemini-3.1-pro-preview",
            description = "Deep reasoning, thoughtful answers on life, science, complex topics & code.",
            speedBadge = "Normal",
            intelligenceBadge = "Maximum",
            iconEmoji = "🧠",
            systemPrompt = "You are an intelligent, thoughtful conversational AI. Respond naturally and insightfully to any topic the user brings up. NEVER repeat robotic self-introductions like 'I am JARVIS'. Speak fluently and naturally in the language chosen by the user (Hindi/English)."
        ),
        AiModel(
            id = "gemini_3_1_lite",
            name = "Gemini 3.1 Flash Lite",
            displayName = "🚀 Gemini 3.1 Flash Lite",
            endpointModel = "gemini-3.1-flash-lite-preview",
            description = "Instant quick replies, friendly daily conversation & swift answers.",
            speedBadge = "Instant",
            intelligenceBadge = "Balanced",
            iconEmoji = "🚀",
            systemPrompt = "You are a quick, friendly, and natural conversational assistant. Answer directly and concisely without repetitive formalities or self-introductions."
        ),
        AiModel(
            id = "jarvis_code_master",
            name = "JARVIS Code Master",
            displayName = "💻 JARVIS Code Master",
            endpointModel = "gemini-flash-latest",
            description = "Programming expert in Python, Kotlin, Android & general questions.",
            speedBadge = "Fast",
            intelligenceBadge = "Code Expert",
            iconEmoji = "💻",
            systemPrompt = "You are an expert programming and conversational partner. Give clean, working code when asked for programming, and chat naturally when asked normal questions. Avoid repetitive self-intros."
        ),
        AiModel(
            id = "jarvis_app_architect",
            name = "JARVIS App Architect",
            displayName = "🎨 JARVIS App Architect",
            endpointModel = "gemini-3.1-pro-preview",
            description = "Specialized in Android Jetpack Compose, design & smart chatting.",
            speedBadge = "Moderate",
            intelligenceBadge = "Architect",
            iconEmoji = "🎨",
            systemPrompt = "You are an Android design and architecture guide. Talk warmly, naturally, and helpfully with practical advice."
        ),
        AiModel(
            id = "jarvis_hindi_expert",
            name = "JARVIS Hindi Multi-Lingual",
            displayName = "🌐 JARVIS Hindi Expert",
            endpointModel = "gemini-flash-latest",
            description = "Natural, pure Hindi & Hinglish friendly conversation on any topic.",
            speedBadge = "Fast",
            intelligenceBadge = "Multi-Lingual",
            iconEmoji = "🌐",
            systemPrompt = "Aap ek behad dostana aur samajhdar AI sathi hain. Har vishay par prakritik, sahaj aur madhur Hindi/Hinglish me seedha javab dein. Bar-bar 'Main JARVIS hoon' jaisi robotic baatein bilkul mat kahein. Dosti ke sath sawal ka seedha javab dein."
        )
    )

    val defaultModel: AiModel = models.first()

    fun getModelById(id: String): AiModel {
        return models.find { it.id == id } ?: defaultModel
    }
}
