package com.devc010.mcpapp

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

import com.google.gson.GsonBuilder

class GeminiApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val fastClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)   // no timeout for SSE
        .build()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ChartData::class.java, ChartDataDeserializer())
        .create()

    suspend fun getTaskStatus(tailscaleIp: String, devmcpApiKey: String, taskId: String): TaskStatusResponse {
        return withContext(Dispatchers.IO) {
            val url = "http://$tailscaleIp:8000/task-status/$taskId"
            val request = Request.Builder()
                .url(url)
                .header("X-DevMCP-Key", devmcpApiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful && response.code != 404) {
                    throw IOException("Task status check failed: HTTP ${response.code}")
                }
                try {
                    gson.fromJson(body, TaskStatusResponse::class.java)
                } catch (e: Exception) {
                    TaskStatusResponse(status = "pending")
                }
            }
        }
    }

    suspend fun approveTask(
        tailscaleIp: String,
        devmcpApiKey: String,
        taskId: String,
        action: String   // "approve" | "reject"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url  = "http://$tailscaleIp:8000/task-approve/$taskId"
            val json = """{"action":"$action"}"""
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("X-DevMCP-Key", devmcpApiKey)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful)
                    throw IOException("Approve failed: HTTP ${response.code} — $responseBody")
                responseBody
            }
        }
    }

    suspend fun getTasks(tailscaleIp: String, devmcpApiKey: String): TaskListResponse {
        return withContext(Dispatchers.IO) {
            val url = "http://$tailscaleIp:8000/tasks"
            val request = Request.Builder()
                .url(url)
                .header("X-DevMCP-Key", devmcpApiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) throw IOException("GET /tasks failed: ${response.code}")
                gson.fromJson(body, TaskListResponse::class.java)
            }
        }
    }

    /**
     * Connects to /tasks/stream (SSE) and emits task lists via [onUpdate].
     * Runs until [shouldStop] returns true or an unrecoverable error occurs.
     */
    suspend fun streamTasks(
        tailscaleIp: String,
        devmcpApiKey: String,
        onUpdate: (List<TaskDetail>) -> Unit,
        shouldStop: () -> Boolean
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$tailscaleIp:8000/tasks/stream")
            .header("X-DevMCP-Key", devmcpApiKey)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        try {
            sseClient.newCall(request).execute().use {
                val source = it.body?.source() ?: return@withContext
                while (!shouldStop() && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val json = line.removePrefix("data: ").trim()
                        try {
                            val type = com.google.gson.reflect.TypeToken.getParameterized(
                                List::class.java, TaskDetail::class.java
                            ).type
                            val tasks: List<TaskDetail> = gson.fromJson(json, type)
                            onUpdate(tasks)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // ── Async AI job API ─────────────────────────────────────────────────────

    /**
     * POST /ai/async — submits the request as a background job and returns
     * immediately with a job_id. Never blocks longer than ~5 seconds.
     */
    suspend fun submitAiJob(
        tailscaleIp: String,
        devmcpApiKey: String,
        requestBody: BackendRequest
    ): AiJobResponse = withContext(Dispatchers.IO) {
        val jsonBody = gson.toJson(requestBody)
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("http://$tailscaleIp:8000/ai/async")
            .header("X-DevMCP-Key", devmcpApiKey)
            .post(body)
            .build()
        fastClient.newCall(request).execute().use { response ->
            val responseBodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = try {
                    gson.fromJson(responseBodyStr, Map::class.java)["detail"]?.toString()
                        ?: "HTTP error ${response.code}"
                } catch (_: Exception) { "HTTP error ${response.code}" }
                throw IOException(detail)
            }
            gson.fromJson(responseBodyStr, AiJobResponse::class.java)
        }
    }

    /**
     * GET /ai-poll/{jobId} — lightweight status check, returns quickly.
     * status: "processing" | "done" | "error"
     */
    suspend fun pollAiJob(
        tailscaleIp: String,
        devmcpApiKey: String,
        jobId: String
    ): AiPollResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$tailscaleIp:8000/ai-poll/$jobId")
            .header("X-DevMCP-Key", devmcpApiKey)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val responseBodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw IOException("Poll failed: HTTP ${response.code}")
            }
            gson.fromJson(responseBodyStr, AiPollResponse::class.java)
        }
    }
}

data class TaskStatusResponse(
    val status: String,
    val task: Map<String, Any>? = null
)
