package com.devc010.mcpapp

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class BackendRequest(
    val prompt: String,
    @SerializedName("groq_api_key") val groqApiKey: String,
    @SerializedName("zai_api_key") val zaiApiKey: String = ""
)

data class TaskInfo(
    @SerializedName("task_id") val taskId: String,
    val status: String,
    val mode: String = "create"
)

data class BackendResponse(
    val result: String? = null,
    val chart: ChartData? = null,
    val task: TaskInfo? = null
)

data class ChartData(
    val type: String,
    val title: String,
    val labels: List<String>,
    @SerializedName("series_labels") val seriesLabels: List<String>? = null,
    val values: List<List<Float>>
)

class ChartDataDeserializer : JsonDeserializer<ChartData> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ChartData {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val title = obj.get("title").asString
        val labels = obj.get("labels").asJsonArray.map { it.asString }
        val seriesLabels = obj.get("series_labels")?.asJsonArray?.map { it.asString }
        
        val valuesElement = obj.get("values").asJsonArray
        val values = mutableListOf<List<Float>>()
        if (valuesElement.size() > 0) {
            val first = valuesElement.get(0)
            if (first.isJsonArray) {
                // Nested list: [[1, 2], [3, 4]]
                for (el in valuesElement) {
                    values.add(el.asJsonArray.map { it.asFloat })
                }
            } else {
                // Flat list: [1, 2, 3]
                values.add(valuesElement.map { it.asFloat })
            }
        }
        return ChartData(type, title, labels, seriesLabels, values)
    }
}

data class TaskStatusEntry(
    val status: String,
    val at: String
)

data class TaskDetail(
    val id: String = "",
    val type: String = "",
    val mode: String = "create",
    @SerializedName("project_name") val projectName: String = "",
    val description: String = "",
    @SerializedName("target_path") val targetPath: String = "",
    val status: String = "pending",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("diff_preview") val diffPreview: String? = null,
    @SerializedName("status_history") val statusHistory: List<TaskStatusEntry> = emptyList(),
    @SerializedName("_folder") val folder: String = ""
)

data class TaskListResponse(
    val tasks: List<TaskDetail>,
    val total: Int
)

// ── Async AI job models ──────────────────────────────────────────────

/** Returned by POST /ai/async — holds the job_id to poll. */
data class AiJobResponse(
    @SerializedName("job_id") val jobId: String
)

/** Returned by GET /ai-poll/{job_id}. */
data class AiPollResponse(
    val status: String,                              // "processing" | "done" | "error"
    val result: Map<String, Any>? = null,            // full payload when status=="done"
    val error: String? = null,                       // message when status=="error"
    @SerializedName("elapsed_ms") val elapsedMs: Double? = null
)
