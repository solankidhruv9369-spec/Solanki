package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.Meeting
import com.example.data.Task
import com.example.data.SystemLog
import com.example.ui.theme.*
import com.example.util.DateTimeUtils
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSpeak: (String) -> Unit = {},
    onStartListening: () -> Unit = {},
    onStopListening: () -> Unit = {}
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val systemLogs by viewModel.systemLogs.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val isHandsFreeMode by viewModel.isHandsFreeMode.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0: Chat, 1: Schedule, 2: Tasks, 3: Meetings, 4: Insights

    // Voice & Zoya States
    var isMuted by remember { mutableStateOf(false) }
    var buildAppDialogState by remember { mutableStateOf<com.example.gemini.AssistantBuildAppAction?>(null) }

    // TTS & Voice triggers
    val latestMessage = chatMessages.lastOrNull()
    var lastSpokenMessageId by remember { mutableStateOf(-1) }

    LaunchedEffect(chatMessages) {
        if (latestMessage != null && latestMessage.sender == "assistant" && latestMessage.id != lastSpokenMessageId) {
            lastSpokenMessageId = latestMessage.id
            if (!isMuted) {
                onSpeak(latestMessage.message)
            } else {
                viewModel.updateVoiceState("IDLE")
            }
        }
    }

    // Action flow listener for phone integrations
    LaunchedEffect(key1 = true) {
        viewModel.assistantActionFlow.collect { action ->
            when (action.type) {
                "OPEN_APP" -> {
                    action.openApp?.let { app ->
                        val appName = app.appName.lowercase()
                        try {
                            val intent = when (appName) {
                                "youtube" -> context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com"))
                                "whatsapp" -> context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                                    ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com"))
                                "settings" -> Intent(android.provider.Settings.ACTION_SETTINGS)
                                "calendar" -> Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/"))
                                "maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=restaurants"))
                                "gmail" -> context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                                    ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)
                                "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                                "call" -> Intent(Intent.ACTION_DIAL)
                                else -> context.packageManager.getLaunchIntentForPackage(appName)
                            }
                            if (intent != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "App $appName not found, searching online!", Toast.LENGTH_SHORT).show()
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com/search?q=$appName")))
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open $appName: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "DIAL_NUMBER" -> {
                    action.dialNumber?.let { dial ->
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dial.phoneNumber}"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not dial number: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "OPEN_WEBSITE" -> {
                    action.openWebsite?.let { web ->
                        try {
                            var formattedUrl = web.url
                            if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                                formattedUrl = "https://$formattedUrl"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open link: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                "BUILD_APP" -> {
                    action.buildApp?.let { build ->
                        buildAppDialogState = build
                    }
                }
            }
        }
    }

    // Manual creation dialog states
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddMeetingDialog by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "AI Chat") },
                    label = { Text("AI Core") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("nav_tab_chat")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Calendar") },
                    label = { Text("Schedule") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("nav_tab_schedule")
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Task, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("nav_tab_tasks")
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Meetings") },
                    label = { Text("Meetings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("nav_tab_meetings")
                )
                NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Insights") },
                    label = { Text("Insights") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.testTag("nav_tab_insights")
                )
            }
        },
        floatingActionButton = {
            if (activeTab == 2 || activeTab == 1) {
                FloatingActionButton(
                    onClick = { showAddTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_task")
                ) {
                    Icon(Icons.Default.AddTask, contentDescription = "Add Task")
                }
            } else if (activeTab == 3) {
                FloatingActionButton(
                    onClick = { showAddMeetingDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_meeting")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Meeting")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        ) {
            Crossfade(targetState = activeTab, label = "tab_fade") { tab ->
                when (tab) {
                    0 -> ChatTab(
                        messages = chatMessages,
                        isGenerating = isGenerating,
                        onSendMessage = { viewModel.sendMessage(it) },
                        onClearChat = { viewModel.clearChatHistory() },
                        voiceState = voiceState,
                        onVoiceStateChange = { viewModel.updateVoiceState(it) },
                        isMuted = isMuted,
                        onMuteToggle = { isMuted = it },
                        isHandsFreeMode = isHandsFreeMode,
                        onToggleHandsFreeMode = { viewModel.toggleHandsFreeMode() },
                        onStartListening = onStartListening,
                        onStopListening = onStopListening,
                        systemLogs = systemLogs,
                        onClearSystemLogs = { viewModel.clearSystemLogs() },
                        audioAmplitude = audioAmplitude
                    )
                    1 -> ScheduleTab(
                        meetings = meetings,
                        tasks = tasks,
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.selectDate(it) },
                        onToggleTask = { viewModel.toggleTaskCompleted(it) },
                        onDeleteTask = { viewModel.deleteTask(it) },
                        onDeleteMeeting = { viewModel.deleteMeeting(it) }
                    )
                    2 -> TasksTab(
                        tasks = tasks,
                        onToggleTask = { viewModel.toggleTaskCompleted(it) },
                        onDeleteTask = { viewModel.deleteTask(it) }
                    )
                    3 -> MeetingsTab(
                        meetings = meetings,
                        onDeleteMeeting = { viewModel.deleteMeeting(it) }
                    )
                    4 -> InsightsTab(
                        meetings = meetings,
                        tasks = tasks,
                        onNavigateToChat = { activeTab = 0 }
                    )
                }
            }

            // --- DIALOGS ---
            if (showAddTaskDialog) {
                AddTaskDialog(
                    onDismiss = { showAddTaskDialog = false },
                    onConfirm = { title, desc, date, priority, category ->
                        viewModel.addTask(title, desc, date, priority, category)
                        showAddTaskDialog = false
                    }
                )
            }

            if (showAddMeetingDialog) {
                AddMeetingDialog(
                    onDismiss = { showAddMeetingDialog = false },
                    onConfirm = { title, desc, startTime, endTime, loc ->
                        viewModel.addMeeting(title, desc, startTime, endTime, loc)
                        showAddMeetingDialog = false
                    }
                )
            }

            // Interactive App-Building Simulated Terminal Overlay
            buildAppDialogState?.let { buildAction ->
                Dialog(onDismissRequest = { buildAppDialogState = null }) {
                    var progress by remember { mutableStateOf(0f) }
                    val logLines = remember {
                        mutableStateListOf(
                            "Initializing Zoya Compiler Environment v1.0...",
                            "Target: ${buildAction.appName}",
                            "Design Parameters: ${buildAction.description}",
                            "Configuring Gradle Kotlin DSL build scripts..."
                        )
                    }

                    LaunchedEffect(key1 = true) {
                        // Progress animation timer
                        for (i in 1..20) {
                            kotlinx.coroutines.delay(200)
                            progress = i * 0.05f
                            when (i) {
                                4 -> logLines.add("Compiling Compose UI assets & Material Theme...")
                                8 -> logLines.add("Synthesizing neural logic pathways...")
                                12 -> logLines.add("Injecting flirty dialogue and sass scripts...")
                                16 -> logLines.add("Executing lint verification and Proguard optimization...")
                                20 -> logLines.add("Injected sub-app successfully into virtual workspace!")
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("simulated_build_dialog")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DeveloperMode,
                                        contentDescription = "Compiler Mode",
                                        tint = Color(0xFF00E5FF)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ZOYA COMPILER CORE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                IconButton(onClick = { buildAppDialogState = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Terminal", tint = Color.LightGray)
                                }
                            }

                            // Terminal Log Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black)
                                    .padding(8.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(logLines) { line ->
                                        Text(
                                            text = "> $line",
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = if (line.contains("successfully")) Color(0xFF00E676) else Color(0xFF00E5FF)
                                        )
                                    }
                                }
                            }

                            // Progress Bar
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (progress < 1f) "Synthesizing Compose UI components..." else "Compilation successful!",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF)
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color(0xFF1B2A32)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { buildAppDialogState = null }) {
                                    Text(
                                        text = if (progress < 1f) "Compile in background" else "Acknowledge",
                                        color = Color(0xFF00E5FF)
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

// ==========================================
// 1. AI ZOYA VOICE CORE TAB (HUD DESIGN)
// ==========================================
@Composable
fun ChatTab(
    messages: List<ChatMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    voiceState: String,
    onVoiceStateChange: (String) -> Unit,
    isMuted: Boolean,
    onMuteToggle: (Boolean) -> Unit,
    isHandsFreeMode: Boolean,
    onToggleHandsFreeMode: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    systemLogs: List<SystemLog>,
    onClearSystemLogs: () -> Unit,
    audioAmplitude: Float
) {
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember { mutableStateOf("") }
    var showHistoryLog by remember { mutableStateOf(false) }
    var isLogsSheetOpen by remember { mutableStateOf(false) }

    // Scroll to bottom when history log is shown and messages update
    LaunchedEffect(messages.size, showHistoryLog) {
        if (showHistoryLog && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Concentric breathing pulse animation for the orb
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == "LISTENING") 1.5f else if (voiceState == "SPEAKING") 1.3f else 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState == "LISTENING") 2.0f else if (voiceState == "SPEAKING") 1.6f else 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEB0B0F19)) // Deep deep Space Obsidian backdrop
    ) {
        // Futuristic grid background or subtle styling
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 36.dp, top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // HUD Top Panel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (voiceState) {
                                    "LISTENING" -> Color(0xFFE040FB) // Magenta flash
                                    "THINKING" -> Color(0xFF00E5FF) // Electric cyan spinning
                                    "SPEAKING" -> Color(0xFF00E676) // Vivid green
                                    else -> Color(0xFF9E9E9E)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ZOYA CORE v3.1",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 1.5.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Hands-Free Mode Toggle (Continuous Listening)
                    IconButton(
                        onClick = onToggleHandsFreeMode,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isHandsFreeMode) Color(0x3300E5FF) else Color(0x1F00E5FF).copy(alpha = 0.1f))
                            .testTag("hands_free_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Hands-Free Mode",
                            tint = if (isHandsFreeMode) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f)
                        )
                    }

                    // Mute / Voice Speaker toggle
                    IconButton(
                        onClick = { onMuteToggle(!isMuted) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0x33FF1744) else Color(0x1F00E5FF))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute Vocal Sass",
                            tint = if (isMuted) Color(0xFFFF1744) else Color(0xFF00E5FF)
                        )
                    }

                    // Reset Session
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x1FFFFFFF))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Clear History",
                            tint = Color.White
                        )
                    }
                }
            }

            // Central Interactive Holographic Orb Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Background aura/glow rings
                Box(
                    modifier = Modifier
                        .size(180.dp * pulseScale2)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (voiceState == "LISTENING") Color(0x40E040FB) else Color(0x2000E5FF),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(140.dp * pulseScale1)
                        .clip(CircleShape)
                        .border(
                            width = 1.5.dp,
                            color = if (voiceState == "LISTENING") Color(0x80E040FB) else Color(0x4000E5FF),
                            shape = CircleShape
                        )
                )

                // The Central Glowing Core
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE040FB), // Magenta
                                    Color(0xFF00E5FF)  // Electric Cyan
                                )
                            )
                        )
                        .clickable {
                            if (voiceState == "LISTENING") {
                                onVoiceStateChange("IDLE")
                                onStopListening()
                            } else {
                                onVoiceStateChange("LISTENING")
                                onStartListening()
                            }
                        }
                        .testTag("voice_orb_core"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (voiceState) {
                            "LISTENING" -> Icons.Default.Mic
                            "THINKING" -> Icons.Default.HourglassEmpty
                            "SPEAKING" -> Icons.Default.Hearing
                            else -> Icons.Default.MicNone
                        },
                        contentDescription = "Zoya Consciousness Core",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }

                // Dynamic Status labels below orb
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (voiceState) {
                            "LISTENING" -> "LISTENING TO YOU..."
                            "THINKING" -> "COMPOSING SASS..."
                            "SPEAKING" -> "ZOYA SPEAKING..."
                            else -> "TAP ORB TO TALK TO ME"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (voiceState == "LISTENING") Color(0xFFE040FB) else Color(0xFF00E5FF),
                        letterSpacing = 1.sp
                    )

                    if (isHandsFreeMode) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x1A00E5FF)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0x3300E5FF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                )
                                Text(
                                    text = "HANDS-FREE ACTIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Glowing Canvas-Based Waveform Visualizer
                    GlowingWaveformVisualizer(
                        voiceState = voiceState,
                        audioAmplitude = audioAmplitude,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(72.dp)
                            .testTag("glowing_waveform_visualizer")
                    )
                }
            }

            // Collapsible / Expendable Holographic Logs & Inputs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistoryLog = !showHistoryLog }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showHistoryLog) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle logs",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showHistoryLog) "HIDE HOLOGRAPHIC CHAT CONSOLE" else "SHOW HOLOGRAPHIC CHAT CONSOLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        letterSpacing = 1.sp
                    )
                }

                if (showHistoryLog) {
                    // Chat Log Terminal
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
                            .background(Color(0x99050A12))
                            .padding(12.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages) { msg ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (msg.sender == "user") Alignment.End else Alignment.Start
                                ) {
                                    Text(
                                        text = if (msg.sender == "user") "YOU" else "ZOYA",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.sender == "user") Color(0xFFE040FB) else Color(0xFF00E5FF)
                                    )
                                    Text(
                                        text = msg.message,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (msg.sender == "user") Color(0x1FE040FB) else Color(0x1F00E5FF)
                                            )
                                            .padding(8.dp)
                                    )
                                }
                            }
                            if (isGenerating) {
                                item {
                                    Text(
                                        text = "Zoya is computing...",
                                        fontSize = 11.sp,
                                        color = Color(0xAA00E5FF)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Suggestion Chips
                    val quickPrompts = listOf(
                        "🌐 Open my GitHub",
                        "📅 Schedule meeting at 4 PM",
                        "📝 Add task: Review Solanki code",
                        "📞 Call Mom",
                        "✨ Build an app"
                    )
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(quickPrompts) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0x1F00E5FF),
                                border = BorderStroke(1.dp, Color(0x3300E5FF)),
                                modifier = Modifier
                                    .clickable {
                                        if (!isGenerating) {
                                            val query = prompt.substringAfter(" ")
                                            onSendMessage(query)
                                        }
                                    }
                                    .testTag("suggestion_chip_${prompt.hashCode()}")
                            ) {
                                Text(
                                    text = prompt,
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0F7FA),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Text Input override so users can still type when quiet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Or text me if you're too shy to speak...", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 2,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                keyboardType = KeyboardType.Text
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() && !isGenerating) {
                                        onSendMessage(inputText)
                                        inputText = ""
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0x4000E5FF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isGenerating) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = inputText.isNotBlank() && !isGenerating,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotBlank() && !isGenerating) Color(0xFF00E5FF)
                                    else Color(0x1F00E5FF)
                                )
                                .testTag("send_chat_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send text",
                                tint = if (inputText.isNotBlank() && !isGenerating) Color.Black else Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Subtle, sleek bottom swipe indicator/handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(Color(0x0F00E5FF))
                .clickable { isLogsSheetOpen = true }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount.y < -15) {
                            isLogsSheetOpen = true
                        }
                    }
                }
                .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Swipe Up logs",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "SWIPE UP / TAP FOR COGNITIVE LOGS",
                    color = Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Hidden Slide-up system command and voice interaction log component
        AnimatedVisibility(
            visible = isLogsSheetOpen,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 400)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(durationMillis = 400)
            ) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF50B0F19)) // Space Obsidian theme with high opacity
                    .clickable(enabled = true, onClick = {}) // Block tap-throughs
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount.y > 15) {
                                isLogsSheetOpen = false
                            }
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = "Telemetry logs",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ZOYA TELEMETRY CONSOLE",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF00E5FF),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "REAL-TIME VOICE COGNITION & SYSTEM DISPATCH TRACES",
                                    fontSize = 8.sp,
                                    color = Color.LightGray,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Purge Button
                            IconButton(
                                onClick = onClearSystemLogs,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33FF1744))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Purge logs",
                                    tint = Color(0xFFFF1744),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            // Close Button
                            IconButton(
                                onClick = { isLogsSheetOpen = false },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1F00E5FF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse logs",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Swipe down instruction banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                            .background(Color(0x0A00E5FF))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Swipe Down",
                            tint = Color(0xAA00E5FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SWIPE DOWN TO COLLAPSE CONSOLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xAA00E5FF),
                            letterSpacing = 1.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Log list
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (systemLogs.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "No events",
                                    tint = Color(0x5500E5FF),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "NO COGNITIVE CORES DISPATCHED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0x5500E5FF),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = "Speak to Zoya or trigger interactive phone/task events to see diagnostic streams.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(systemLogs) { log ->
                                    val (badgeColor, typeTag) = when (log.type) {
                                        "VOICE_INPUT" -> Color(0xFFE040FB) to "[VOICE_IN]"
                                        "VOICE_RESPONSE" -> Color(0xFF00E5FF) to "[ZOYA_VOX]"
                                        else -> Color(0xFF00E676) to "[SYS_CMD]"
                                    }
                                    
                                    val formattedTime = remember(log.timestamp) {
                                        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                                        format.format(Date(log.timestamp))
                                    }

                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D121F)),
                                        border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = typeTag,
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = log.title,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Text(
                                                    text = formattedTime,
                                                    color = Color.Gray,
                                                    fontSize = 10.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFF050811))
                                                    .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(4.dp))
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = log.content,
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp,
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    lineHeight = 15.sp
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
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (isUser) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(containerColor)
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = message.message,
                    color = textColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun GeneratingPlaceholderBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 240.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Syncing schedule...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ==========================================
// 2. UNIFIED SCHEDULE / CALENDAR TAB
// ==========================================
@Composable
fun ScheduleTab(
    meetings: List<Meeting>,
    tasks: List<Task>,
    selectedDate: Long,
    onDateSelected: (Long) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onDeleteMeeting: (Meeting) -> Unit
) {
    // Generate horizontal days row for 14 days (7 back, 7 forward)
    val datesList = remember {
        val list = mutableListOf<Long>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -3) // Start 3 days ago
        for (i in 0..14) {
            list.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        list
    }

    // Filtered lists for the selected date
    val dayMeetings = remember(meetings, selectedDate) {
        meetings.filter { DateTimeUtils.isSameDay(it.startTime, selectedDate) }
    }
    val dayTasks = remember(tasks, selectedDate) {
        tasks.filter { DateTimeUtils.isSameDay(it.dueDate, selectedDate) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Daily Schedule",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = DateTimeUtils.formatDate(selectedDate),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal scrolling calendar bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(datesList) { itemDate ->
                val cal = Calendar.getInstance().apply { timeInMillis = itemDate }
                val dayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: ""
                val dayNumber = cal.get(Calendar.DAY_OF_MONTH).toString()
                val isSelected = DateTimeUtils.isSameDay(itemDate, selectedDate)

                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onDateSelected(itemDate) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayNumber,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Meetings & Tasks Combined List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Meetings Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Meetings (${dayMeetings.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (dayMeetings.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Outlined.Groups,
                        message = "No meetings scheduled for this day."
                    )
                }
            } else {
                items(dayMeetings) { meeting ->
                    MeetingScheduleItem(meeting, onDeleteMeeting)
                }
            }

            // Tasks Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Due Tasks (${dayTasks.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (dayTasks.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Outlined.Task,
                        message = "No tasks due on this day."
                    )
                }
            } else {
                items(dayTasks) { task ->
                    TaskScheduleItem(task, onToggleTask, onDeleteTask)
                }
            }
        }
    }
}

@Composable
fun MeetingScheduleItem(meeting: Meeting, onDelete: (Meeting) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("meeting_item_card_${meeting.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time box
            Column(
                modifier = Modifier.width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = DateTimeUtils.formatTime(meeting.startTime),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "to",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = DateTimeUtils.formatTime(meeting.endTime),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meeting.description.isNotBlank()) {
                    Text(
                        text = meeting.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Location",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = meeting.location,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(onClick = { onDelete(meeting) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Meeting",
                    tint = ColorError.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun TaskScheduleItem(task: Task, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_item_card_${task.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle(task) },
                colors = CheckboxDefaults.colors(checkedColor = ColorSuccess)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    // Category Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.category,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Priority Badge
                    val pColor = when (task.priority) {
                        "High" -> ColorHighPriority
                        "Medium" -> ColorMediumPriority
                        else -> ColorLowPriority
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 9.sp,
                            color = pColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = { onDelete(task) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = ColorError.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun EmptySectionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// 3. DEDICATED TASKS MANAGER TAB
// ==========================================
@Composable
fun TasksTab(
    tasks: List<Task>,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit
) {
    var filterCategory by remember { mutableStateOf("All") }
    var filterPriority by remember { mutableStateOf("All") }
    val categories = listOf("All", "Work", "Personal", "Shopping", "General")
    val priorities = listOf("All", "High", "Medium", "Low")

    val filteredTasks = remember(tasks, filterCategory, filterPriority) {
        tasks.filter { task ->
            (filterCategory == "All" || task.category.lowercase() == filterCategory.lowercase()) &&
            (filterPriority == "All" || task.priority.lowercase() == filterPriority.lowercase())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Tasks Manager",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Categories filters row
        Text(
            text = "Category",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = filterCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { filterCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Priority filter row
        Text(
            text = "Priority",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(priorities) { pri ->
                val isSelected = filterPriority == pri
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { filterPriority = pri }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pri,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tasks list
        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.AssignmentTurnedIn,
                        contentDescription = "No tasks",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "All caught up! No tasks found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks) { task ->
                    TaskCard(task, onToggleTask, onDeleteTask)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: (Task) -> Unit, onDelete: (Task) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_manager_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle(task) },
                colors = CheckboxDefaults.colors(checkedColor = ColorSuccess)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurface
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = "Due Date",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = DateTimeUtils.formatDateTime(task.dueDate),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Priority color coding
                    val pColor = when (task.priority) {
                        "High" -> ColorHighPriority
                        "Medium" -> ColorMediumPriority
                        else -> ColorLowPriority
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = task.priority,
                            fontSize = 9.sp,
                            color = pColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = { onDelete(task) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ColorError.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ==========================================
// 4. MEETINGS TAB
// ==========================================
@Composable
fun MeetingsTab(
    meetings: List<Meeting>,
    onDeleteMeeting: (Meeting) -> Unit
) {
    val upcomingMeetings = remember(meetings) {
        meetings.filter { it.startTime >= System.currentTimeMillis() - 3600000 } // show future or active meetings
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Meetings Agenda",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (upcomingMeetings.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Groups,
                        contentDescription = "No meetings",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No upcoming meetings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(upcomingMeetings) { meeting ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("meeting_agenda_card_${meeting.id}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = meeting.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onDeleteMeeting(meeting) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Cancel Meeting",
                                        tint = ColorError.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            if (meeting.description.isNotBlank()) {
                                Text(
                                    text = meeting.description,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            Divider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = "Time",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${DateTimeUtils.formatTime(meeting.startTime)} - ${DateTimeUtils.formatTime(meeting.endTime)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = "Date",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(meeting.startTime)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "Location",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = meeting.location,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. DASHBOARD & INSIGHTS TAB
// ==========================================
@Composable
fun InsightsTab(
    meetings: List<Meeting>,
    tasks: List<Task>,
    onNavigateToChat: () -> Unit
) {
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.isCompleted }
    val pendingTasks = totalTasks - completedTasks
    val completionRate = if (totalTasks > 0) (completedTasks.toFloat() / totalTasks * 100).toInt() else 0

    val highPriorityCount = tasks.count { it.priority == "High" && !it.isCompleted }
    val todayMeetingsCount = meetings.count { DateTimeUtils.isSameDay(it.startTime, System.currentTimeMillis()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "AI Smart Insights",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's Task Completion",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$completedTasks of $totalTasks tasks completed",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = completionRate.toFloat() / 100f,
                        modifier = Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 6.dp
                    )
                    Text(
                        text = "$completionRate%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stat Grid Rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Active Meetings", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = todayMeetingsCount.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "scheduled for today", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "High Priority Tasks", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = highPriorityCount.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ColorHighPriority)
                    Text(text = "pending tasks left", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI Advice Box
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Tip",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Productivity Recommendation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                val aiAdviceText = when {
                    highPriorityCount > 0 -> {
                        "You have $highPriorityCount high-priority task(s) left. Consider using the AI Core to automatically brainstorm subtasks or optimize your time block."
                    }
                    todayMeetingsCount > 3 -> {
                        "Your agenda today is packed with $todayMeetingsCount meetings. Ensure you allocate 15-minute buffers in between meetings to rest and stay efficient."
                    }
                    completionRate == 100 && totalTasks > 0 -> {
                        "Awesome! You completed all tasks on your schedule today! Excellent work, enjoy your productive day."
                    }
                    else -> {
                        "Your daily task progress is solid. To schedule meetings with friends or teammates seamlessly, open up the AI Core and describe your goal!"
                    }
                }
                Text(
                    text = aiAdviceText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToChat,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Ask Assistant", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Developer Profile & GitHub Repository Card
        var showGitSyncGuide by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_repo_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x3300E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = "Repository",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Dhruv Solanki",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "@solankidhruv9369-spec",
                                fontSize = 11.sp,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x2200E676),
                        border = BorderStroke(1.dp, Color(0x5500E676))
                    ) {
                        Text(
                            text = "origin/main",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Linked GitHub Repository:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "solankidhruv9369-spec/Solanki",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/solankidhruv9369-spec/Solanki")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_github_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Icon(
                            Icons.Default.OpenInBrowser,
                            contentDescription = "Open GitHub",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Repo", fontSize = 11.sp, color = Color(0xFF00E5FF))
                    }

                    Button(
                        onClick = { showGitSyncGuide = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("git_sync_guide_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sync Info",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Push & Sync", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }

        if (showGitSyncGuide) {
            AlertDialog(
                onDismissRequest = { showGitSyncGuide = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GitHub Sync Guide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Repository: solankidhruv9369-spec/Solanki",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "1. AI Studio Sync: Click the top-right Settings/Export menu in your browser and select 'Push to GitHub'.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = "2. Git Origin: Pre-configured to https://github.com/solankidhruv9369-spec/Solanki.git with initial commit ready on branch 'main'.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            text = "3. Zoya Voice Integration: You can ask Zoya anytime: 'Open my GitHub' or 'Show my Solanki repo'!",
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGitSyncGuide = false }) {
                        Text("Got it")
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// 6. ADD TASK DIALOG
// ==========================================
@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String, String, Long, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Medium") }
    var category by remember { mutableStateOf("General") }

    val priorities = listOf("High", "Medium", "Low")
    val categories = listOf("General", "Work", "Personal", "Shopping")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_task_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add Daily Task",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_dialog_title"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Task Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority Row
                Text(text = "Priority", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    priorities.forEach { p ->
                        val isSelected = priority == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { priority = p }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Category Row
                Text(text = "Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { cat ->
                        val isSelected = category == cat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { category = cat }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                fontSize = 10.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title, desc, System.currentTimeMillis(), priority, category)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.testTag("task_dialog_confirm")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. ADD MEETING DIALOG
// ==========================================
@Composable
fun AddMeetingDialog(onDismiss: () -> Unit, onConfirm: (String, String, Long, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("Online") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_meeting_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Schedule Meeting",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Meeting Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meeting_dialog_title"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location / Video Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val current = System.currentTimeMillis()
                                val oneHour = 3600000L
                                onConfirm(title, desc, current, current + oneHour, location)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.testTag("meeting_dialog_confirm")
                    ) {
                        Text("Schedule")
                    }
                }
            }
        }
    }
}

// ==========================================
// 8. GLOWING WAVEFORM VISUALIZER (CANVAS)
// ==========================================
@Composable
fun GlowingWaveformVisualizer(
    voiceState: String,
    audioAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_transition")
    val phaseShift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1"
    )
    val phaseShift2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (-2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = when (voiceState) {
            "LISTENING" -> {
                val normalizedAmp = (audioAmplitude + 2f).coerceAtLeast(0f) * 6f
                if (normalizedAmp > 3f) normalizedAmp else 3f
            }
            "SPEAKING" -> {
                22f + 28f * kotlin.math.abs(kotlin.math.sin(phaseShift1 * 1.8f))
            }
            "THINKING" -> {
                8f + 5f * kotlin.math.sin(phaseShift1 * 2f)
            }
            else -> {
                2f + 1f * kotlin.math.sin(phaseShift1)
            }
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 120f),
        label = "amplitude_spring"
    )

    Canvas(
        modifier = modifier
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        val path1 = Path()
        val path2 = Path()
        val path3 = Path()

        val pointsCount = 120
        for (i in 0..pointsCount) {
            val x = (i.toFloat() / pointsCount) * width
            val envelope = kotlin.math.sin(Math.PI * i.toDouble() / pointsCount).toFloat()

            // Wave 1: Cyan
            val angle1 = (i.toFloat() / pointsCount) * (2f * Math.PI.toFloat() * 3.2f) + phaseShift1
            val y1 = centerY + (kotlin.math.sin(angle1) * animatedAmplitude * envelope)
            if (i == 0) path1.moveTo(x, y1) else path1.lineTo(x, y1)

            // Wave 2: Magenta
            val angle2 = (i.toFloat() / pointsCount) * (2f * Math.PI.toFloat() * 2.1f) + phaseShift2
            val y2 = centerY + (kotlin.math.cos(angle2) * (animatedAmplitude * 0.7f) * envelope)
            if (i == 0) path2.moveTo(x, y2) else path2.lineTo(x, y2)

            // Wave 3: Neon Blue
            val angle3 = (i.toFloat() / pointsCount) * (2f * Math.PI.toFloat() * 4.6f) + (phaseShift1 - phaseShift2)
            val y3 = centerY + (kotlin.math.sin(angle3) * (animatedAmplitude * 0.45f) * envelope)
            if (i == 0) path3.moveTo(x, y3) else path3.lineTo(x, y3)
        }

        // Draw Wave 1 with glow
        drawPath(
            path = path1,
            color = Color(0x2200E5FF),
            style = Stroke(width = 10.dp.toPx())
        )
        drawPath(
            path = path1,
            color = Color(0x6600E5FF),
            style = Stroke(width = 5.dp.toPx())
        )
        drawPath(
            path = path1,
            color = Color(0xFF00E5FF),
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw Wave 2 with glow
        drawPath(
            path = path2,
            color = Color(0x44E040FB),
            style = Stroke(width = 5.dp.toPx())
        )
        drawPath(
            path = path2,
            color = Color(0xFFE040FB),
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Draw Wave 3 (Vibrant Blue highlight)
        drawPath(
            path = path3,
            color = Color(0xFF00B0FF),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}
