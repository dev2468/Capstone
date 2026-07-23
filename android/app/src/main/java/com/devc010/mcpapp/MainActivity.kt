package com.devc010.mcpapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Colors
val BackgroundColor = Color(0xFF0A0A0A)
val SurfaceColor = Color(0xFF1E1E1E)
val PrimaryBlue = Color(0xFF4285F4)
val PrimaryGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4285F4), Color(0xFF9C27B0))
)
val AccentPurple = Color(0xFF9C27B0)

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DevMCPTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "chat") {
                    composable("chat") {
                        ChatScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToTasks = { navController.navigate("tasks") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable("tasks") {
                        TasksScreen(
                            viewModel = viewModel,
                            onApproveTask = { taskId, action ->
                                // find msgIndex from taskFeed — action comes from Task Feed, not chat
                                viewModel.approveOrRejectTask(taskId, -1, action)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DevMCPTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF4285F4),
        secondary = Color(0xFF9C27B0),
        background = BackgroundColor,
        surface = SurfaceColor,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkColorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit = {}
) {
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Only count non-initial messages (messages added after the greeting)
    val hasUserMessages = messages.any { it.isUser }

    // Auto-scroll to bottom when messages change or loading starts
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1 + (if (isLoading) 1 else 0))
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "Dev MCP",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = onNavigateToTasks) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Task Feed",
                                tint = Color(0xFF90A4AE)
                            )
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = BackgroundColor,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
                // "Thinking..." status bar below top app bar
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300))
                ) {
                    ThinkingStatusBar()
                }
            }
        },
        containerColor = BackgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            if (!hasUserMessages) {
                // Empty state
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState()
                }
            } else {
                // Message List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = messages.filter {
                            it.isUser || it.content.isNotBlank() || it.chart != null
                                || it.taskId != null || it.isAwaitingApproval
                        },
                        key = { it.id }
                    ) { message ->
                        MessageBubbleWithAnimation(
                            message = message,
                            viewModel = viewModel,
                            msgIndex = messages.indexOf(message),
                            onRetry = { viewModel.retryLastMessage() }
                        )
                    }

                    if (isLoading) {
                        item {
                            TypingIndicatorBubble()
                        }
                    }
                }
            }

            // Input Bar
            InputBar(
                isLoading = isLoading,
                onSendMessage = { text ->
                    viewModel.sendMessage(text)
                }
            )
        }
    }
}

@Composable
fun ThinkingStatusBar() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Dev MCP is thinking...",
            color = PrimaryBlue.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        )
    }
}

@Composable
fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Hey Dev 👋",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Ask me anything or give me a task",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = PrimaryBlue.copy(alpha = 0.85f)
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MessageBubbleWithAnimation(message: Message, viewModel: MainViewModel, msgIndex: Int, onRetry: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(450))
    ) {
        MessageBubble(message = message, viewModel = viewModel, msgIndex = msgIndex, onRetry = onRetry)
    }
}

@Composable
fun MessageBubble(message: Message, viewModel: MainViewModel, msgIndex: Int, onRetry: () -> Unit) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val userShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    val aiShape   = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    val shape = if (message.isUser) userShape else aiShape

    // Relative timestamp — recomputes every 30 seconds
    var timestampText by remember(message.id) { mutableStateOf(message.relativeTimestamp()) }
    LaunchedEffect(message.id) {
        while (true) {
            delay(30_000L)
            timestampText = message.relativeTimestamp()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (message.isUser) {
            // ── User bubble: plain text, gradient background ───────────────────
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(shape)
                    .background(PrimaryGradient)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (message.isAwaitingApproval) {
            // ── Review diff card: amber-bordered, visually distinct ─────────────
            DiffReviewCard(
                message = message,
                msgIndex = msgIndex,
                viewModel = viewModel
            )
        } else {
            // ── AI bubble: markdown rendered, full-width ───────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(aiShape)
                    .background(SurfaceColor)
                    .border(
                        1.dp,
                        if (message.isError) Color(0xFFFF5252).copy(alpha = 0.5f)
                        else AccentPurple.copy(alpha = 0.3f),
                        aiShape
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when {
                    message.chart != null -> MdChart(chartData = message.chart)
                    message.taskId != null -> TaskStatusBubble(
                        taskId = message.taskId,
                        status = message.taskStatus ?: "pending",
                        summary = message.content,
                        mode = message.taskMode ?: "create"
                    )
                    else -> MarkdownContent(text = message.content)
                }
            }
        }

        // Retry button for error messages
        if (message.isError && !message.isUser) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry", color = PrimaryBlue, style = MaterialTheme.typography.labelMedium)
            }
        } else if (!message.isAwaitingApproval) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = timestampText,
                color = Color.Gray,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun DiffReviewCard(message: Message, msgIndex: Int, viewModel: MainViewModel) {
    val cardShape  = RoundedCornerShape(16.dp)
    val isApproving = message.taskStatus == "approving"
    val diffText   = message.diffPreview ?: "(no diff preview available)"
    val scrollState = rememberScrollState()

    // Amber accent for the review card — visually distinct from regular AI bubbles
    val amberColor  = Color(0xFFFFB300)
    val cardBg      = Color(0xFF0D1117)
    val approveGreen = Color(0xFF2EA043)
    val rejectRed   = Color(0xFFCF222E)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBg)
            .border(1.5.dp, amberColor.copy(alpha = 0.7f), cardShape)
            .padding(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚠️", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Review Required",
                color = amberColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        message.taskId?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Task: $it",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Diff preview — scrollable monospace box ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161B22))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = diffText,
                color = Color(0xFFE6EDF3),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.verticalScroll(scrollState)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Action buttons / loading state ───────────────────────────────────
        if (isApproving) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = amberColor,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Submitting…",
                        color = amberColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reject
                OutlinedButton(
                    onClick = {
                        message.taskId?.let { tid ->
                            viewModel.approveOrRejectTask(tid, msgIndex, "reject")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = rejectRed
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, rejectRed.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("✗  Reject", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // Approve
                Button(
                    onClick = {
                        message.taskId?.let { tid ->
                            viewModel.approveOrRejectTask(tid, msgIndex, "approve")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = approveGreen,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("✓  Approve", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
                .background(SurfaceColor)
                .border(
                    1.dp,
                    AccentPurple.copy(alpha = 0.3f),
                    RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DotAnimation(0)
                DotAnimation(1)
                DotAnimation(2)
            }
        }
    }
}

@Composable
fun DotAnimation(delayUnit: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = delayUnit * 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(AccentPurple.copy(alpha = alpha))
    )
}

@Composable
fun InputBar(
    isLoading: Boolean,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isEnabled = text.isNotBlank() && !isLoading

    Surface(
        color = BackgroundColor,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { if (!isLoading) text = it },
                placeholder = {
                    Text(
                        if (isLoading) "Waiting for response..." else "Message DevMCP...",
                        color = Color.Gray
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceColor,
                    unfocusedContainerColor = SurfaceColor,
                    disabledContainerColor = SurfaceColor.copy(alpha = 0.6f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = PrimaryBlue
                ),
                enabled = !isLoading,
                maxLines = 5
            )

            val interactionSource =
                remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.9f else 1f,
                label = "buttonScale"
            )

            IconButton(
                onClick = {
                    if (isEnabled) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = isEnabled,
                modifier = Modifier
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) PrimaryGradient
                        else Brush.linearGradient(listOf(Color.DarkGray, Color.DarkGray))
                    )
                    .size(48.dp),
                interactionSource = interactionSource
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun TaskStatusBubble(taskId: String, status: String, summary: String, mode: String = "create") {
    val isPending = status == "pending"
    val isDone = status == "done"

    val modeLabel = if (mode == "modify") "Modifying file" else "Creating file"
    val modeIcon  = if (mode == "modify") "✏️" else "⚙️"
    val dotScale by animateFloatAsState(
        targetValue = if (isPending) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_pulse"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    when {
                        isDone -> Color(0xFF1A3A1A)
                        status == "timeout" -> Color(0xFF3A1A1A)
                        else -> Color(0xFF1A2A3A)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (isPending) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(dotScale)
                        .clip(CircleShape)
                        .background(Color(0xFF4285F4))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$modeIcon $modeLabel via Antigravity…",
                    color = Color(0xFF8AB4F8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else if (isDone) {
                Text(text = "✅", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Done",
                    color = Color(0xFF81C995),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(text = "⚠️", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Timed out",
                    color = Color(0xFFFF8A65),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = summary,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "ID: $taskId",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
