package com.example.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.example.terminal.TerminalDialog
import com.example.ui.components.AiModelSelectorModal
import com.example.ui.components.AppBuilderModal
import com.example.ui.components.AttachmentMenuSheet
import com.example.ui.components.VoiceCallDialog
import com.example.ui.components.VoiceSelectorDialog
import com.example.util.AttachedData
import com.example.util.AttachmentUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onToggleTheme: () -> Unit, voiceManager: VoiceManager) {
    val messages by viewModel.messages.collectAsState()
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val isBuilding by viewModel.isBuilding.collectAsState()
    val buildStep by viewModel.buildStep.collectAsState()

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Dialog & Sheet States
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showAppBuilderModal by remember { mutableStateOf(false) }
    var showTerminalDialog by remember { mutableStateOf(false) }
    var showCallDialog by remember { mutableStateOf(false) }
    var showVoiceSelector by remember { mutableStateOf(false) }
    var showAiModelSelector by remember { mutableStateOf(false) }
    var isVoiceMuted by remember { mutableStateOf(false) }
    var terminalInitialCode by remember { mutableStateOf("") }
    var currentAttachment by remember { mutableStateOf<AttachedData?>(null) }

    // Android Activity Result Launchers
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentUtils.processUri(context, uri)
            currentAttachment = processed
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentUtils.processUri(context, uri)
            currentAttachment = processed
        }
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Speak when a new AI message arrives in clear Hindi voice
    LaunchedEffect(messages) {
        val lastMessage = messages.lastOrNull()
        if (lastMessage != null && !lastMessage.isUser && !isBuilding && !showCallDialog && !isVoiceMuted) {
            voiceManager.speak(lastMessage.text)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "JARVIS Projects",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // + New Chat Button
                    Button(
                        onClick = {
                            viewModel.createNewSession("💬 Project ${sessions.size + 1}")
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Chat / Project")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // + New App Builder Project Button
                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showAppBuilderModal = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("App Builder Project")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Voice Personas Selector in Drawer
                    val currentVoice by voiceManager.currentVoice.collectAsState()
                    FilledTonalButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showVoiceSelector = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(currentVoice.flagEmoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Voice: ${currentVoice.name}", maxLines = 1)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // AI Models Selector in Drawer
                    val activeModel by viewModel.activeAiModel.collectAsState()
                    OutlinedButton(
                        onClick = {
                            scope.launch { drawerState.close() }
                            showAiModelSelector = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(activeModel.iconEmoji, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Model: ${activeModel.name}", maxLines = 1)
                    }
                }

                HorizontalDivider()

                Text(
                    "History & Sessions (${sessions.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Sessions List
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(sessions) { session ->
                        val isSelected = session.id == currentSessionId
                        val isAppSession = session.title.contains("🚀") || session.title.contains("App")

                        NavigationDrawerItem(
                            label = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isAppSession) Icons.Default.RocketLaunch else Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        session.title,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (sessions.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.deleteSession(session.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete project",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.switchSession(session.id)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val activeModel by viewModel.activeAiModel.collectAsState()
                        Surface(
                            onClick = { showAiModelSelector = true },
                            color = Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "JARVIS IDE",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                activeModel.displayName,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = "Switch AI Model",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                                val currentTitle = sessions.find { it.id == currentSessionId }?.title ?: "Chat"
                                Text(
                                    currentTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Projects Menu")
                        }
                    },
                    actions = {
                        // Terminal Action Button
                        IconButton(onClick = {
                            terminalInitialCode = ""
                            showTerminalDialog = true
                        }) {
                            Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = MaterialTheme.colorScheme.primary)
                        }

                        // App Builder Action Button
                        IconButton(onClick = { showAppBuilderModal = true }) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = "App Builder")
                        }

                        // Voice Persona Selector Button
                        IconButton(onClick = { showVoiceSelector = true }) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = "AI Voices", tint = MaterialTheme.colorScheme.primary)
                        }

                        // Voice Speech Mute/Unmute Toggle
                        IconButton(onClick = {
                            isVoiceMuted = !isVoiceMuted
                            if (isVoiceMuted) voiceManager.stop()
                        }) {
                            Icon(
                                if (isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Toggle Voice",
                                tint = if (isVoiceMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Dark/Light Theme Toggle
                        IconButton(onClick = onToggleTheme) {
                            Icon(Icons.Default.Brightness4, contentDescription = "Toggle Theme")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Live App Building Progress Banner
                if (isBuilding) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = buildStep ?: "Building Android Application...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                // Chat Messages or Empty Welcome State
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Kahiye, aaj kya baat karni hai?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Aap koi bhi sawal pooch sakte hain ya normal baat kar sakte hain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Quick starter suggestion chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "Kaise ho aap?" to "Aap kaise hain aur aaj kya chal raha hai?",
                                "Ek mast joke sunao" to "Ek mazedaar chutkula sunao",
                                "Chai kaise banayein?" to "Masala chai banane ka sabse accha tareeqa batao",
                                "Python code example" to "Python me clean code example batao",
                                "Android app banao" to "Mujhe ek Android app banani hai"
                            ).forEach { (label, q) ->
                                SuggestionChip(
                                    onClick = { viewModel.sendMessage(q) },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        state = listState
                    ) {
                        items(messages) { message ->
                            ChatBubble(
                                message = message,
                                onRunInTerminal = { code ->
                                    terminalInitialCode = code
                                    showTerminalDialog = true
                                }
                            )
                        }
                    }
                }

                // Attachment Preview Bar (above input field)
                currentAttachment?.let { attachment ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val previewBitmap = remember(attachment) {
                                if (attachment.isImage && attachment.base64Data != null) {
                                    try {
                                        val bytes = android.util.Base64.decode(attachment.base64Data, android.util.Base64.NO_WRAP)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    } catch (_: Exception) {
                                        null
                                    }
                                } else null
                            }

                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = "Image preview",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                            } else {
                                Icon(
                                    Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    attachment.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    if (attachment.isImage) "Image attached • Ready for AI discussion" else "File attached • ${attachment.mimeType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { currentAttachment = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Bottom Input Row: [+] [Text Field] [Voice Call] [Send]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Plus (+) Button on the LEFT side of text input
                    FilledIconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Attachment or App Builder")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask JARVIS to code, run Python, build app...", fontSize = 13.sp) },
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = {
                        showCallDialog = true
                    }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                    }

                    IconButton(
                        onClick = {
                            val userText = text.trim()
                            val attached = currentAttachment
                            if (userText.isNotEmpty() || attached != null) {
                                if (attached != null) {
                                    viewModel.sendMessageWithAttachment(
                                        text = userText,
                                        mimeType = attached.mimeType,
                                        base64Data = attached.base64Data,
                                        fileName = attached.fileName
                                    )
                                    currentAttachment = null
                                } else {
                                    viewModel.sendMessage(userText)
                                }
                                text = ""
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Attachment Bottom Sheet
    if (showAttachmentSheet) {
        AttachmentMenuSheet(
            onDismiss = { showAttachmentSheet = false },
            onPickImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPickFile = {
                filePickerLauncher.launch("*/*")
            },
            onOpenAppBuilder = {
                showAppBuilderModal = true
            }
        )
    }

    // App Builder Modal
    if (showAppBuilderModal) {
        AppBuilderModal(
            onDismiss = { showAppBuilderModal = false },
            onStartBuild = { appName, prompt ->
                viewModel.startAppBuild(appName, prompt)
            }
        )
    }

    // Terminal Dialog
    if (showTerminalDialog) {
        TerminalDialog(
            initialCode = terminalInitialCode,
            onDismiss = { showTerminalDialog = false }
        )
    }

    // Voice Call Dialog
    if (showCallDialog) {
        VoiceCallDialog(
            voiceManager = voiceManager,
            onQueryAi = { spokenText ->
                viewModel.getAiAnswer(spokenText)
            },
            onDismiss = {
                showCallDialog = false
            }
        )
    }

    // Voice Selector Dialog
    if (showVoiceSelector) {
        VoiceSelectorDialog(
            voiceManager = voiceManager,
            onDismiss = { showVoiceSelector = false }
        )
    }

    // AI Model Selector Modal
    if (showAiModelSelector) {
        val activeModel by viewModel.activeAiModel.collectAsState()
        AiModelSelectorModal(
            selectedModel = activeModel,
            onModelSelected = { model ->
                viewModel.selectAiModel(model)
                showAiModelSelector = false
            },
            onDismiss = { showAiModelSelector = false }
        )
    }
}

@Composable
fun ChatBubble(
    message: Message,
    onRunInTerminal: (String) -> Unit
) {
    val isUser = message.isUser
    val context = LocalContext.current

    // Check if the message contains APK download link
    val apkUrl = remember(message.text) {
        val regex = "https://github\\.com/[^\\s)]+\\.apk".toRegex()
        regex.find(message.text)?.value
    }

    val parts = message.text.split("```")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Code block
                CodeBlock(code = part, onRunInTerminal = onRunInTerminal)
            } else if (part.isNotBlank()) {
                TextBubble(part, isUser)
            }
        }

        // If an APK download link exists in this message, display an interactive Release Card!
        if (apkUrl != null) {
            ApkDownloadCard(
                apkUrl = apkUrl,
                onDownload = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }
    }
}

@Composable
fun ApkDownloadCard(
    apkUrl: String,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Release APK Ready!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        "Compiled & Published to GitHub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download APK (Direct Install)")
            }
        }
    }
}

@Composable
fun TextBubble(text: String, isUser: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp
        ),
        modifier = Modifier.widthIn(max = 340.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun CodeBlock(
    code: String,
    onRunInTerminal: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var showPreview by remember { mutableStateOf(false) }

    // Strip language tag if first line is e.g. python, html, kotlin
    val lines = code.trim().lines()
    val firstLine = lines.firstOrNull()?.trim() ?: ""
    val isPython = firstLine.equals("python", ignoreCase = true) || firstLine.equals("py", ignoreCase = true) || code.contains("print(") || code.contains("def ")
    val isHtml = code.contains("<html>", ignoreCase = true) || code.contains("<div", ignoreCase = true) || code.contains("<!DOCTYPE", ignoreCase = true)

    val cleanCode = if (firstLine.matches("^[a-zA-Z0-9_-]+$".toRegex()) && lines.size > 1) {
        lines.drop(1).joinToString("\n")
    } else {
        code.trim()
    }

    if (showPreview) {
        CodePreviewDialog(cleanCode) { showPreview = false }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = cleanCode,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Run in Terminal Button
                FilledTonalButton(
                    onClick = { onRunInTerminal(cleanCode) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = "Run", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run in Terminal", fontSize = 11.sp)
                }

                // Web Preview button
                if (isHtml) {
                    FilledTonalButton(
                        onClick = { showPreview = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(Icons.Default.RemoveRedEye, contentDescription = "Preview", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Preview", fontSize = 11.sp)
                    }
                }

                // Copy button
                OutlinedButton(
                    onClick = { clipboardManager.setText(AnnotatedString(cleanCode)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun CodePreviewDialog(code: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Interactive App Preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (code.contains("<html>", ignoreCase = true) || code.contains("<div", ignoreCase = true) || code.contains("<!DOCTYPE", ignoreCase = true)) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadDataWithBaseURL(null, code, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        onRelease = { webView -> webView.destroy() }
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                code.trim(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Preview")
                }
            }
        }
    }
}
