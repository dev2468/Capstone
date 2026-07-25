# DevMCP Kafka Event Schema Specification

## Overview

This document defines the official message schemas and topic contracts for **DevMCP**. 
All communication between clients (Desktop, Android), the **Reactive Agent**, the **Proactive Brain**, and observability sinks relies on these four namespaced Kafka topics.

## Topic Summary

| Topic Name | Producer | Consumer(s) | Retain / Partition Key | Description |
|---|---|---|---|---|
| `devmcp.commands` | Desktop, Android | Reactive Agent | `session_id` | User commands (text or transcribed voice) |
| `devmcp.events` | Reactive Agent | Desktop Log Panel, DynamoDB | `task_id` | Granular event stream of agent execution steps |
| `devmcp.context` | Proactive Brain | Reactive Agent | `user_id` | RAG updates, file index changes, and background memory synthesis |
| `devmcp.status` | Reactive Agent | Desktop UI, Android Remote | `task_id` | High-level task status and progress updates |

---

## 1. Topic: `devmcp.commands`

### Description
The `devmcp.commands` topic receives all commands initiated by users via the Tauri Desktop application or the Android companion app.

### Schema Definition

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "DevMCPCommand",
  "type": "object",
  "required": [
    "message_id",
    "session_id",
    "user_id",
    "device",
    "input_type",
    "input",
    "timestamp"
  ],
  "properties": {
    "message_id": { "type": "string", "description": "Unique UUID for the message" },
    "session_id": { "type": "string", "description": "Active session identifier" },
    "user_id": { "type": "string", "description": "User identifier" },
    "device": { "type": "string", "enum": ["desktop", "android"] },
    "input_type": { "type": "string", "enum": ["text", "voice"] },
    "input": { "type": "string", "description": "Raw prompt text or transcribed speech" },
    "metadata": {
      "type": "object",
      "properties": {
        "audio_duration_ms": { "type": "number" },
        "client_version": { "type": "string" }
      }
    },
    "timestamp": { "type": "string", "format": "date-time" }
  }
}
```

### Example Payload

```json
{
  "message_id": "msg_987f6543-e21b-12d3-a456-426614174000",
  "session_id": "sess_dev_20260723_01",
  "user_id": "dev_user_01",
  "device": "desktop",
  "input_type": "voice",
  "input": "open YouTube and play lo-fi beats",
  "metadata": {
    "audio_duration_ms": 2400,
    "client_version": "1.0.0"
  },
  "timestamp": "2026-07-23T10:00:00Z"
}
```

---

## 2. Topic: `devmcp.events`

### Description
The `devmcp.events` topic streams real-time state transitions and diagnostic telemetry from the **Reactive Agent**. This topic feeds the **Tauri Log Panel** and the **DynamoDB Audit Store**.

### Event Types Catalog

| Event Type | Producer Trigger | Key Data Included |
|---|---|---|
| `task_created` | Reactive Agent receives new command | `task_id`, `input`, `session_id` |
| `llm_thinking` | Agent starts model inference | `model_name`, `provider` |
| `llm_response` | LLM outputs text/reasoning | `prompt_tokens`, `completion_tokens`, `content` |
| `tool_call_started` | Agent invokes tool | `tool_name`, `arguments`, `execution_id` |
| `tool_call_completed` | Tool completes execution | `execution_id`, `result`, `duration_ms` |
| `tool_call_failed` | Tool encounters error | `execution_id`, `error_code`, `error_message` |
| `approval_requested` | High-risk tool requires permission | `approval_id`, `tool_name`, `proposed_args` |
| `approval_received` | User responds to approval modal | `approval_id`, `approved` (boolean), `reason` |
| `rag_context_fetched` | Vector search returns RAG chunks | `query`, `chunk_count`, `top_score` |
| `browser_action` | Playwright tool executes browser action | `action_type`, `url`, `selector` |
| `task_completed` | Full task pipeline finishes | `task_id`, `total_duration_ms`, `summary` |
| `task_failed` | Task unrecoverably fails | `task_id`, `error_message`, `stack_trace` |

### Generic Event Wrapper Schema

```json
{
  "event_id": "evt_12345678-abcd-1234-ef00-123456789abc",
  "task_id": "task_45678",
  "session_id": "sess_dev_20260723_01",
  "event_type": "string",
  "timestamp": "2026-07-23T10:00:01Z",
  "payload": {}
}
```

### Event Payload Examples

#### `tool_call_started`
```json
{
  "event_id": "evt_001",
  "task_id": "task_456",
  "session_id": "sess_dev_20260723_01",
  "event_type": "tool_call_started",
  "timestamp": "2026-07-23T10:00:02Z",
  "payload": {
    "execution_id": "exec_789",
    "tool_name": "playwright_browser",
    "action": "navigate_and_search",
    "args": {
      "url": "https://youtube.com",
      "search_query": "lo-fi beats"
    }
  }
}
```

#### `approval_requested`
```json
{
  "event_id": "evt_002",
  "task_id": "task_456",
  "session_id": "sess_dev_20260723_01",
  "event_type": "approval_requested",
  "timestamp": "2026-07-23T10:00:05Z",
  "payload": {
    "approval_id": "appr_999",
    "tool_name": "system_command",
    "description": "Execute shell script to update system settings",
    "command": "powershell -Command Remove-Item -Path ./temp/*"
  }
}
```

#### `rag_context_fetched`
```json
{
  "event_id": "evt_003",
  "task_id": "task_456",
  "session_id": "sess_dev_20260723_01",
  "event_type": "rag_context_fetched",
  "timestamp": "2026-07-23T10:00:01.500Z",
  "payload": {
    "query": "open YouTube and play lo-fi beats",
    "chunk_count": 3,
    "top_similarity_score": 0.91,
    "sources": ["history_preferences.json", "browser_shortcuts.md"]
  }
}
```

---

## 3. Topic: `devmcp.context`

### Description
The `devmcp.context` topic is published to by the **Proactive Brain** (running background embedding & indexing pipelines on EC2). It delivers proactive knowledge updates and RAG index changes to the **Reactive Agent**.

### Schema Definition

```json
{
  "context_id": "ctx_5551212",
  "user_id": "dev_user_01",
  "update_type": "vector_index_updated",
  "timestamp": "2026-07-23T09:55:00Z",
  "payload": {
    "source_type": "file_watcher",
    "file_path": "c:/Users/HP/Desktop/devmcp-repo/docs/ARCHITECTURE.md",
    "action": "indexed",
    "chunk_count": 5,
    "embedding_model": "text-embedding-3-small"
  }
}
```

### Event Types for Context
- `vector_index_updated`: Triggered when document or codebase file changes are re-embedded into RDS `pgvector`.
- `memory_synthesized`: Triggered when the background brain distills long-term user preferences or recent task summaries.

---

## 4. Topic: `devmcp.status`

### Description
The `devmcp.status` topic conveys user-facing status indicators and overall task resolution progress. Both Desktop and Android apps listen to this topic to keep status indicators in sync.

### Schema Definition

```json
{
  "task_id": "task_456",
  "session_id": "sess_dev_20260723_01",
  "user_id": "dev_user_01",
  "status": "in_progress",
  "progress_percentage": 50,
  "current_step": "Searching for lo-fi beats stream on YouTube",
  "result": null,
  "error": null,
  "timestamp": "2026-07-23T10:00:03Z"
}
```

### Allowed Status Values
- `queued`: Command accepted, waiting for agent processing.
- `in_progress`: Agent actively running LLM loops or calling tools.
- `waiting_approval`: Agent paused waiting for user permission.
- `completed`: Task successfully finished with result.
- `failed`: Task halted due to fatal error.

---

## Partitioning Strategy

- `devmcp.commands`: Partition key = `session_id` (guarantees strict ordering of user commands per session).
- `devmcp.events`: Partition key = `task_id` (ensures sequential ordering of event log streams per task).
- `devmcp.context`: Partition key = `user_id` (groups context updates by user).
- `devmcp.status`: Partition key = `task_id` (guarantees latest status updates overwrite sequentially per task).
