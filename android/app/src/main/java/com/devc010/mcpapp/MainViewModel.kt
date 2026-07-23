package com.devc010.mcpapp

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.GsonBuilder
import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val chart: ChartData? = null,
    val taskId: String? = null,
    val taskStatus: String? = null,       // "pending", "done", "timeout", "awaiting_approval", "approving", "rejected"
    val taskMode: String? = null,         // "create" or "modify"
    val isAwaitingApproval: Boolean = false,
    val diffPreview: String? = null
)

fun Message.relativeTimestamp(): String {
    val diff = System.currentTimeMillis() - createdAt
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "$seconds secs ago"
        minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
        else -> "$hours hr${if (hours > 1) "s" else ""} ago"
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _messages = mutableStateListOf<Message>()
    val messages: List<Message> = _messages

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // Task feed — updated via SSE stream or polling fallback
    private val _taskFeed = MutableStateFlow<List<TaskDetail>>(emptyList())
    val taskFeed: StateFlow<List<TaskDetail>> = _taskFeed
    private var streamJob: Job? = null
    private var streamActive = false

    private val sharedPreferences =
        application.getSharedPreferences("SettingsPrefs", Context.MODE_PRIVATE)
    private val apiClient = GeminiApiClient()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ChartData::class.java, ChartDataDeserializer())
        .create()

    // Tracks the last user message text for the Retry feature
    private var lastUserMessageText: String = ""

    init {
        // Initial greeting
        _messages.add(
            Message(
                content = "Hello! I'm DevMCP, your personal AI assistant. How can I help you today?",
                isUser = false
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        streamActive = false
        streamJob?.cancel()
    }

    /** Connect to /tasks/stream (SSE). Falls back to polling /tasks every 5s on error. */
    fun startTaskStream() {
        if (streamJob?.isActive == true) return
        streamActive = true
        val tailscaleIp  = sharedPreferences.getString("tailscale_ip",   "") ?: ""
        val devmcpApiKey = sharedPreferences.getString("devmcp_api_key", "") ?: ""
        if (tailscaleIp.isBlank() || devmcpApiKey.isBlank()) return
        streamJob = viewModelScope.launch {
            var reconnectDelayMs = 3000L
            val maxReconnectDelayMs = 30000L
            while (streamActive) {
                val sseConnectedAt = System.currentTimeMillis()
                try {
                    // Try SSE first
                    apiClient.streamTasks(
                        tailscaleIp, devmcpApiKey,
                        onUpdate = { tasks -> _taskFeed.value = tasks },
                        shouldStop = { !streamActive }
                    )
                    if (System.currentTimeMillis() - sseConnectedAt > 10_000L) {
                        reconnectDelayMs = 3000L
                    }
                } catch (_: Exception) {}

                // SSE dropped — fall back to polling with exponential backoff
                if (streamActive) {
                    android.util.Log.d("DevMCP", "SSE disconnected, reconnecting in ${reconnectDelayMs}ms")
                    delay(reconnectDelayMs)
                    reconnectDelayMs = minOf(reconnectDelayMs * 2, maxReconnectDelayMs)

                    try {
                        val resp = apiClient.getTasks(tailscaleIp, devmcpApiKey)
                        _taskFeed.value = resp.tasks
                    } catch (_: Exception) {}
                    delay(5_000L)
                }
            }
        }
    }

    fun stopTaskStream() {
        streamActive = false
        streamJob?.cancel()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return

        lastUserMessageText = text
        val userMessage = Message(content = text, isUser = true)
        _messages.add(userMessage)
        fetchAiResponse()
    }

    fun retryLastMessage() {
        if (lastUserMessageText.isBlank() || _isLoading.value) return
        // Remove the last error bubble
        val lastIndex = _messages.indexOfLast { it.isError }
        if (lastIndex >= 0) _messages.removeAt(lastIndex)
        fetchAiResponse()
    }

    private fun fetchAiResponse() {
        val tailscaleIp = sharedPreferences.getString("tailscale_ip", "") ?: ""
        val groqApiKey = sharedPreferences.getString("groq_api_key", "") ?: ""
        val zaiApiKey = sharedPreferences.getString("zai_api_key", "") ?: ""
        val devmcpApiKey = sharedPreferences.getString("devmcp_api_key", "") ?: ""

        if (tailscaleIp.isBlank()) {
            _messages.add(
                Message(
                    content = "Please set your Tailscale IP in Settings",
                    isUser = false,
                    isError = true
                )
            )
            return
        }

        if (groqApiKey.isBlank()) {
            _messages.add(
                Message(
                    content = "Please set your Groq API key in Settings",
                    isUser = false,
                    isError = true
                )
            )
            return
        }

        if (devmcpApiKey.isBlank()) {
            _messages.add(
                Message(
                    content = "Please set your DevMCP API key in Settings",
                    isUser = false,
                    isError = true
                )
            )
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            // Formulate conversation history as a single string (last 4 messages, no system prompt)
            // Include task messages (content is blank but taskId is set) so the LLM knows a task
            // was already dispatched and doesn't re-trigger the same work on the next message.
            val validMessages = _messages.filter { (it.content.isNotBlank() || it.taskId != null) && !it.isError }
            var promptString = validMessages.takeLast(4).joinToString("\n") { message ->
                val role = if (message.isUser) "User" else "Assistant"
                val text = if (message.taskId != null && message.content.isBlank()) {
                    val action = when (message.taskMode) {
                        "modify" -> "modified"
                        else     -> "created"
                    }
                    "I've queued a code task (ID: ${message.taskId}) to be $action. Status: ${message.taskStatus ?: "pending"}."
                } else {
                    message.content
                }
                "$role: $text"
            }

            if (promptString.length > 9000) {
                val rawCut = promptString.takeLast(9000)
                val firstNewlineIndex = rawCut.indexOf('\n')
                promptString = if (firstNewlineIndex >= 0 && firstNewlineIndex < rawCut.length - 1) {
                    rawCut.substring(firstNewlineIndex + 1)
                } else {
                    rawCut
                }
                android.util.Log.w("DevMCP", "Prompt truncated to 9000 chars to stay under backend limit")
            }

            android.util.Log.d("DevMCP", "Sending prompt to backend: $promptString")

            try {
                val request = BackendRequest(
                    prompt = promptString,
                    groqApiKey = groqApiKey,
                    zaiApiKey = zaiApiKey
                )

                // ── Step 1: Submit job — returns instantly with a job_id ───────
                android.util.Log.d("DevMCP", "Submitting async job to http://$tailscaleIp:8000/ai/async")
                val jobResponse = apiClient.submitAiJob(tailscaleIp, devmcpApiKey, request)
                val jobId = jobResponse.jobId
                android.util.Log.d("DevMCP", "Job submitted: $jobId — starting poll loop")

                // ── Step 2: Poll until done (up to 5 minutes, every 2 seconds) ─
                val maxAttempts = 150   // 150 × 2s = 300s = 5 minutes
                var attempts = 0
                var resolved = false

                while (attempts < maxAttempts) {
                    delay(2_000L)
                    attempts++

                    val poll = try {
                        apiClient.pollAiJob(tailscaleIp, devmcpApiKey, jobId)
                    } catch (e: Exception) {
                        android.util.Log.w("DevMCP", "Poll attempt $attempts failed: ${e.message}")
                        continue   // transient network error — keep trying
                    }

                    android.util.Log.d("DevMCP", "Poll $attempts: status=${poll.status}")

                    when (poll.status) {
                        "done" -> {
                            val payload = poll.result
                            val resultText  = payload?.get("result") as? String
                            val chartRaw    = payload?.get("chart")
                            val taskRaw     = payload?.get("task")

                            when {
                                chartRaw != null -> {
                                    // Re-parse the chart from the nested map via Gson round-trip
                                    val chartJson = gson.toJson(chartRaw)
                                    val chart = try {
                                        gson.fromJson(chartJson, ChartData::class.java)
                                    } catch (_: Exception) { null }
                                    if (chart != null) {
                                        _messages.add(Message(content = "", isUser = false, chart = chart))
                                    } else {
                                        _messages.add(Message(content = "[Chart render failed]", isUser = false, isError = true))
                                    }
                                }
                                taskRaw != null -> {
                                    val taskJson = gson.toJson(taskRaw)
                                    val taskInfo = try {
                                        gson.fromJson(taskJson, TaskInfo::class.java)
                                    } catch (_: Exception) { null }
                                    if (taskInfo != null) {
                                        val msgIndex = _messages.size
                                        _messages.add(
                                            Message(
                                                content = "",
                                                isUser = false,
                                                taskId = taskInfo.taskId,
                                                taskStatus = "pending",
                                                taskMode = taskInfo.mode
                                            )
                                        )
                                        android.util.Log.d("DevMCP", "Task queued: ${taskInfo.taskId} [${taskInfo.mode}]")
                                        viewModelScope.launch {
                                            pollTaskStatus(taskInfo.taskId, msgIndex, tailscaleIp, devmcpApiKey)
                                        }
                                    }
                                }
                                resultText != null -> {
                                    _messages.add(Message(content = resultText, isUser = false))
                                    android.util.Log.d("DevMCP", "Got result after $attempts polls (~${attempts * 2}s)")
                                }
                                else -> {
                                    _messages.add(
                                        Message(content = "Received an empty response.", isUser = false, isError = true)
                                    )
                                }
                            }
                            resolved = true
                            break
                        }
                        "error" -> {
                            val errMsg = poll.error ?: "Unknown backend error"
                            android.util.Log.e("DevMCP", "Job $jobId failed: $errMsg")
                            _messages.add(Message(content = "Error: $errMsg", isUser = false, isError = true))
                            resolved = true
                            break
                        }
                        // "processing" — keep looping
                    }
                }

                if (!resolved) {
                    android.util.Log.e("DevMCP", "Job $jobId timed out after ${maxAttempts * 2}s")
                    _messages.add(
                        Message(
                            content = "Request timed out after 5 minutes. The backend may still be processing — try again.",
                            isUser = false,
                            isError = true
                        )
                    )
                }

            } catch (e: Exception) {
                android.util.Log.e("DevMCP", "Error in async AI flow", e)
                _messages.add(
                    Message(
                        content = "Error: ${e.message}",
                        isUser = false,
                        isError = true
                    )
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun pollTaskStatus(taskId: String, msgIndex: Int, tailscaleIp: String, devmcpApiKey: String) {
        val maxAttempts = 60  // poll up to 5 minutes (60 x 5s)
        var attempts = 0
        while (attempts < maxAttempts) {
            delay(5_000L)
            attempts++
            try {
                val statusResponse = apiClient.getTaskStatus(tailscaleIp, devmcpApiKey, taskId)
                android.util.Log.d("DevMCP", "Task $taskId poll $attempts: ${statusResponse.status}")
                when (statusResponse.status) {
                    "done" -> {
                        val taskData = statusResponse.task
                        val summary = taskData?.get("summary")?.toString()
                            ?: "Code task completed by Antigravity."
                        val filesWritten = taskData?.get("files_written")?.toString() ?: ""
                        val resultContent = if (filesWritten.isNotBlank()) "$summary\n\n📁 $filesWritten" else summary
                        withContext(Dispatchers.Main) {
                            if (msgIndex < _messages.size) {
                                _messages[msgIndex] = _messages[msgIndex].copy(
                                    content = resultContent,
                                    taskStatus = "done",
                                    isAwaitingApproval = false
                                )
                            }
                        }
                        return
                    }
                    "awaiting_approval" -> {
                        val taskData = statusResponse.task
                        val diffPreview = taskData?.get("diff_preview")?.toString() ?: ""
                        android.util.Log.d("DevMCP", "Task $taskId awaiting approval — surfacing review card")
                        withContext(Dispatchers.Main) {
                            if (msgIndex < _messages.size) {
                                _messages[msgIndex] = _messages[msgIndex].copy(
                                    isAwaitingApproval = true,
                                    diffPreview = diffPreview,
                                    taskStatus = "awaiting_approval"
                                )
                            }
                        }
                        return  // stop polling — UI buttons take over
                    }
                    "failed" -> {
                        withContext(Dispatchers.Main) {
                            if (msgIndex < _messages.size) {
                                _messages[msgIndex] = _messages[msgIndex].copy(
                                    content = statusResponse.task?.get("error")?.toString()
                                        ?: "Task failed on the server.",
                                    taskStatus = "timeout",
                                    isError = true
                                )
                            }
                        }
                        return
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("DevMCP", "Task poll failed: ${e.message}")
            }
        }
        // Timeout after 5 minutes
        withContext(Dispatchers.Main) {
            if (msgIndex < _messages.size) {
                _messages[msgIndex] = _messages[msgIndex].copy(
                    content = "Task timed out. Check Antigravity for status.",
                    taskStatus = "timeout",
                    isError = true
                )
            }
        }
    }

    fun approveOrRejectTask(taskId: String, msgIndex: Int, action: String) {
        val tailscaleIp  = sharedPreferences.getString("tailscale_ip",   "") ?: ""
        val devmcpApiKey = sharedPreferences.getString("devmcp_api_key", "") ?: ""
        viewModelScope.launch {
            // Show loading spinner inside the card (only if tied to a chat bubble)
            withContext(Dispatchers.Main) {
                if (msgIndex >= 0 && msgIndex < _messages.size)
                    _messages[msgIndex] = _messages[msgIndex].copy(taskStatus = "approving")
            }

            val result = apiClient.approveTask(tailscaleIp, devmcpApiKey, taskId, action)
            result.fold(
                onSuccess = {
                    android.util.Log.d("DevMCP", "Task $taskId $action succeeded")
                    withContext(Dispatchers.Main) {
                        if (msgIndex >= 0 && msgIndex < _messages.size) {
                            val confirmText = if (action == "approve") "Approved — applying now" else "Rejected"
                            _messages[msgIndex] = _messages[msgIndex].copy(
                                isAwaitingApproval = false,
                                taskStatus = if (action == "approve") "pending" else "rejected",
                                content = confirmText
                            )
                        }
                    }
                    // Resume polling only for approve so the final done/failed result appears
                    if (action == "approve" && msgIndex >= 0) {
                        viewModelScope.launch {
                            pollTaskStatus(taskId, msgIndex, tailscaleIp, devmcpApiKey)
                        }
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("DevMCP", "Task $taskId $action failed", e)
                    withContext(Dispatchers.Main) {
                        if (msgIndex >= 0 && msgIndex < _messages.size)
                            _messages[msgIndex] = _messages[msgIndex].copy(
                                taskStatus = "awaiting_approval"
                            )
                        _messages.add(
                            Message(
                                content = "${action.replaceFirstChar { it.uppercase() }} failed: ${e.message}",
                                isUser = false,
                                isError = true
                            )
                        )
                    }
                }
            )
        }
    }
}
