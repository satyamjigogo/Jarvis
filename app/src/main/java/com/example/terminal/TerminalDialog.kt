package com.example.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.coding.CodeExecutionService
import com.example.coding.SupportedLanguage
import kotlinx.coroutines.launch

data class TerminalLog(
    val command: String?,
    val output: String,
    val isError: Boolean = false,
    val durationMs: Long = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalDialog(
    initialCode: String = "",
    onDismiss: () -> Unit
) {
    val languages = CodeExecutionService.supportedLanguages
    val detectedLangId = remember(initialCode) {
        if (initialCode.isNotBlank()) CodeExecutionService.detectLanguage(initialCode) else "python"
    }

    var selectedLanguage by remember {
        mutableStateOf(languages.find { it.id == detectedLangId } ?: languages.first())
    }

    var selectedTab by remember { mutableStateOf(if (initialCode.isNotBlank()) 1 else 0) }
    var codeText by remember {
        mutableStateOf(
            if (initialCode.isNotBlank()) initialCode
            else selectedLanguage.sampleCode
        )
    }
    var stdinInput by remember { mutableStateOf("") }
    var showStdinBox by remember { mutableStateOf(false) }
    var scriptOutput by remember { mutableStateOf<ExecutionResult?>(null) }
    var isRunningCode by remember { mutableStateOf(false) }
    var commandInput by remember { mutableStateOf("") }
    var showLanguageDropdown by remember { mutableStateOf(false) }

    val logs = remember {
        mutableStateListOf(
            TerminalLog(
                null,
                "⚡ Real Multi-Language Cloud Compiler Sandbox (Piston Linux Engine)\nLanguages: Python, Kotlin, Java, JavaScript, C++, Bash\nRun code on real cloud containers with live stdout/stderr."
            )
        )
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // If initialCode is provided, auto-run on real compiler
    LaunchedEffect(initialCode) {
        if (initialCode.isNotBlank()) {
            isRunningCode = true
            val res = CodeExecutionService.executeCode(selectedLanguage.id, initialCode, stdinInput)
            scriptOutput = res
            isRunningCode = false
            logs.add(
                TerminalLog(
                    command = "${selectedLanguage.id} <snippet>",
                    output = res.output,
                    isError = res.isError,
                    durationMs = res.executionTimeMs
                )
            )
            scope.launch {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    val terminalBg = Color(0xFF0D1117)
    val terminalHeader = Color(0xFF161B22)
    val greenColor = Color(0xFF3FB950)
    val cyanColor = Color(0xFF58A6FF)
    val errorColor = Color(0xFFF85149)
    val textPrimary = Color(0xFFC9D1D9)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = terminalBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(terminalHeader)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Traffic light dots
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFF5F56)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFFFBD2E)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF27C93F)))
                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        tint = cyanColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "JARVIS Cloud Compiler",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Copy logs
                    IconButton(
                        onClick = {
                            val allText = logs.joinToString("\n") { (it.command?.let { c -> "$ $c\n" } ?: "") + it.output }
                            clipboardManager.setText(AnnotatedString(allText))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs", tint = textPrimary, modifier = Modifier.size(16.dp))
                    }

                    // Close
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textPrimary, modifier = Modifier.size(18.dp))
                    }
                }

                // Tab Selector: [Console] [Online Compiler Runner]
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = terminalHeader,
                    contentColor = cyanColor,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("CLI Terminal", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("${selectedLanguage.iconEmoji} Cloud Compiler", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // CLI Console View
                    Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp)) {
                        // Quick commands row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("help", "python --version", "node -v", "uname -a", "date", "clear").forEach { cmd ->
                                SuggestionChip(
                                    onClick = {
                                        if (cmd == "clear") {
                                            logs.clear()
                                        } else {
                                            scope.launch {
                                                val res = CodeExecutionService.executeCode("bash", cmd)
                                                logs.add(TerminalLog(cmd, res.output, res.isError, res.executionTimeMs))
                                                listState.animateScrollToItem(logs.size - 1)
                                            }
                                        }
                                    },
                                    label = { Text(cmd, color = cyanColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = Color(0xFF21262D))
                                )
                            }
                        }

                        // Terminal output lines
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF090D13), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            state = listState
                        ) {
                            items(logs) { log ->
                                if (log.command != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "sandbox@piston:~$ ",
                                            color = greenColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            log.command,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (log.durationMs > 0) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                "[${log.durationMs}ms]",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = log.output,
                                    color = if (log.isError) errorColor else textPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }

                        // Input command bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "$ ",
                                color = greenColor,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            TextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Enter shell or python command...", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (commandInput.isNotBlank()) {
                                        val cmd = commandInput.trim()
                                        commandInput = ""
                                        if (cmd.equals("clear", ignoreCase = true)) {
                                            logs.clear()
                                        } else {
                                            scope.launch {
                                                val res = CodeExecutionService.executeCode("bash", cmd)
                                                logs.add(TerminalLog(cmd, res.output, res.isError, res.executionTimeMs))
                                                listState.animateScrollToItem(logs.size - 1)
                                            }
                                        }
                                    }
                                }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF161B22),
                                    unfocusedContainerColor = Color(0xFF161B22),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (commandInput.isNotBlank()) {
                                        val cmd = commandInput.trim()
                                        commandInput = ""
                                        if (cmd.equals("clear", ignoreCase = true)) {
                                            logs.clear()
                                        } else {
                                            scope.launch {
                                                val res = CodeExecutionService.executeCode("bash", cmd)
                                                logs.add(TerminalLog(cmd, res.output, res.isError, res.executionTimeMs))
                                                listState.animateScrollToItem(logs.size - 1)
                                            }
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = greenColor)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Run", tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    // Multi-Language Cloud Compiler Runner View
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Top Toolbar: [Language Selector Dropdown] [Stdin toggle] [Run Online Button]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Language Dropdown Selector
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF21262D),
                                    modifier = Modifier.clickable { showLanguageDropdown = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedLanguage.iconEmoji, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            selectedLanguage.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = showLanguageDropdown,
                                    onDismissRequest = { showLanguageDropdown = false },
                                    modifier = Modifier.background(Color(0xFF161B22))
                                ) {
                                    languages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(lang.iconEmoji, fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column {
                                                        Text(lang.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text("v${lang.version}", color = Color.Gray, fontSize = 10.sp)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedLanguage = lang
                                                showLanguageDropdown = false
                                                if (codeText.isBlank() || languages.any { it.sampleCode == codeText }) {
                                                    codeText = lang.sampleCode
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Stdin toggle
                                IconButton(
                                    onClick = { showStdinBox = !showStdinBox },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Input,
                                        contentDescription = "Standard Input",
                                        tint = if (showStdinBox) cyanColor else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Real Cloud Execution Button
                                Button(
                                    onClick = {
                                        if (!isRunningCode) {
                                            isRunningCode = true
                                            scope.launch {
                                                val res = CodeExecutionService.executeCode(
                                                    language = selectedLanguage.id,
                                                    code = codeText,
                                                    stdin = stdinInput
                                                )
                                                scriptOutput = res
                                                isRunningCode = false
                                                logs.add(
                                                    TerminalLog(
                                                        command = "${selectedLanguage.id} main.${selectedLanguage.extension}",
                                                        output = res.output,
                                                        isError = res.isError,
                                                        durationMs = res.executionTimeMs
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (isRunningCode) Color.Gray else greenColor),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isRunningCode
                                ) {
                                    if (isRunningCode) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Compiling...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run Online", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // Optional Stdin input field
                        if (showStdinBox) {
                            TextField(
                                value = stdinInput,
                                onValueChange = { stdinInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                placeholder = { Text("Program stdin (inputs to input() / Scanner)...", color = Color.Gray, fontSize = 11.sp) },
                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF161B22),
                                    unfocusedContainerColor = Color(0xFF161B22)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                maxLines = 2
                            )
                        }

                        // Code Editor Input Area
                        TextField(
                            value = codeText,
                            onValueChange = { codeText = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF090D13), RoundedCornerShape(8.dp)),
                            textStyle = TextStyle(
                                color = Color(0xFFE6EDF3),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            ),
                            placeholder = { Text("Write or paste ${selectedLanguage.name} code here...", color = Color.Gray, fontSize = 12.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF090D13),
                                unfocusedContainerColor = Color(0xFF090D13),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Output Display Box
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 150.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Cloud Output (${selectedLanguage.name}):",
                                            color = cyanColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    scriptOutput?.let { res ->
                                        Text(
                                            if (res.isError) "Status: Failed" else "Status: Success [${res.executionTimeMs}ms]",
                                            color = if (res.isError) errorColor else greenColor,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                    item {
                                        Text(
                                            text = scriptOutput?.output ?: "Click 'Run Online' to execute on real Linux container.",
                                            color = if (scriptOutput?.isError == true) errorColor else textPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
