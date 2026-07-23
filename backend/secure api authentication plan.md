# Shared-Secret Authentication — Implementation Plan

Add `DEVMCP_API_KEY` authentication to every API route on the Python server,
and wire the matching `X-DevMCP-Key` header into the Android app.

---

## Proposed Changes

### Python Server — `api.py`

#### [MODIFY] [api.py](file:///C:/Users/HP/Desktop/MCP/api.py)

```diff
 from fastapi import FastAPI, HTTPException
+from fastapi import Depends
+from fastapi.security import APIKeyHeader
 from fastapi.middleware.cors import CORSMiddleware
 from pydantic import BaseModel
 import os
+import hmac
 import subprocess
 ...

+# ── Auth ──────────────────────────────────────────────────────
+_API_KEY_HEADER = APIKeyHeader(name="X-DevMCP-Key", auto_error=False)
+
+def _load_api_key() -> str:
+    key = os.environ.get("DEVMCP_API_KEY", "")
+    if not key:
+        raise RuntimeError(
+            "DEVMCP_API_KEY environment variable is not set. "
+            "Set it before starting the server."
+        )
+    return key
+
+_DEVMCP_API_KEY: str = _load_api_key()   # fail-fast at import time
+
+async def verify_api_key(key: str | None = Depends(_API_KEY_HEADER)) -> None:
+    if not key or not hmac.compare_digest(key, _DEVMCP_API_KEY):
+        raise HTTPException(status_code=401, detail="Invalid or missing X-DevMCP-Key")
```

Routes gain `dependencies=[Depends(verify_api_key)]`:

```diff
-@app.get("/health")
-async def health():
+@app.get("/health", dependencies=[Depends(verify_api_key)])
+async def health():

-@app.post("/tool")
-async def call_tool(request: ToolRequest):
+@app.post("/tool", dependencies=[Depends(verify_api_key)])
+async def call_tool(request: ToolRequest):

-@app.post("/ai")
-async def ai_endpoint(request: AIRequest):
+@app.post("/ai", dependencies=[Depends(verify_api_key)])
+async def ai_endpoint(request: AIRequest):

-@app.get("/task-status/{task_id}")
-async def task_status(task_id: str):
+@app.get("/task-status/{task_id}", dependencies=[Depends(verify_api_key)])
+async def task_status(task_id: str):
```

---

### Android — `SettingsViewModel.kt`

#### [MODIFY] [SettingsViewModel.kt](file:///C:/Users/HP/AndroidStudioProjects/MCPAPP/app/src/main/java/com/devc010/mcpapp/SettingsViewModel.kt)

```diff
+    private val _devmcpApiKey = MutableStateFlow(sharedPreferences.getString("devmcp_api_key", "") ?: "")
+    val devmcpApiKey: StateFlow<String> = _devmcpApiKey.asStateFlow()

+    fun updateDevmcpApiKey(key: String) {
+        _devmcpApiKey.value = key
+        sharedPreferences.edit().putString("devmcp_api_key", key).apply()
+    }
```

Also update `testConnection()` to pass the header:

```diff
-                    val connection = url.openConnection() as HttpURLConnection
-                    connection.requestMethod = "GET"
+                    val connection = url.openConnection() as HttpURLConnection
+                    connection.requestMethod = "GET"
+                    connection.setRequestProperty("X-DevMCP-Key", _devmcpApiKey.value)
```

---

### Android — `SettingsScreen.kt`

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/HP/AndroidStudioProjects/MCPAPP/app/src/main/java/com/devc010/mcpapp/SettingsScreen.kt)

Add a new `devmcpApiKey` state variable and a third `OutlinedTextField` between the
Groq key field and the spacer:

```diff
+    val devmcpApiKey by viewModel.devmcpApiKey.collectAsState()
+    var devmcpKeyVisible by remember { mutableStateOf(false) }

     // After Groq API Key field ...
+    Spacer(modifier = Modifier.height(16.dp))

+    // DevMCP API Key Input
+    OutlinedTextField(
+        value = devmcpApiKey,
+        onValueChange = { viewModel.updateDevmcpApiKey(it) },
+        label = { Text("DevMCP API Key") },
+        placeholder = { Text("Shared secret for the MCP server", color = Color.Gray) },
+        modifier = Modifier.fillMaxWidth(),
+        colors = textFieldColors,
+        singleLine = true,
+        visualTransformation = if (devmcpKeyVisible) VisualTransformation.None
+                               else PasswordVisualTransformation(),
+        trailingIcon = {
+            IconButton(onClick = { devmcpKeyVisible = !devmcpKeyVisible }) {
+                Icon(
+                    imageVector = if (devmcpKeyVisible) Icons.Filled.Visibility
+                                  else Icons.Filled.VisibilityOff,
+                    contentDescription = if (devmcpKeyVisible) "Hide" else "Show",
+                    tint = Color.LightGray
+                )
+            }
+        }
+    )
```

---

### Android — `GeminiApiClient.kt`

#### [MODIFY] [GeminiApiClient.kt](file:///C:/Users/HP/AndroidStudioProjects/MCPAPP/app/src/main/java/com/devc010/mcpapp/GeminiApiClient.kt)

Add `devmcpApiKey` parameter to both public functions and attach the header:

```diff
-    suspend fun generateContent(tailscaleIp: String, requestBody: BackendRequest): BackendResponse {
+    suspend fun generateContent(tailscaleIp: String, devmcpApiKey: String, requestBody: BackendRequest): BackendResponse {
         return withContext(Dispatchers.IO) {
             ...
             val request = Request.Builder()
                 .url(url)
+                .header("X-DevMCP-Key", devmcpApiKey)
                 .post(body)
                 .build()

-    suspend fun getTaskStatus(tailscaleIp: String, taskId: String): TaskStatusResponse {
+    suspend fun getTaskStatus(tailscaleIp: String, devmcpApiKey: String, taskId: String): TaskStatusResponse {
         return withContext(Dispatchers.IO) {
-            val request = Request.Builder().url(url).get().build()
+            val request = Request.Builder()
+                .url(url)
+                .header("X-DevMCP-Key", devmcpApiKey)
+                .get()
+                .build()
```

> [!IMPORTANT]
> `MainViewModel.kt` also calls both these functions. After updating `GeminiApiClient`,
> those call-sites must be updated to pass `devmcpApiKey`. I'll read `MainViewModel.kt`
> to do the correct minimal patch.

---

## Security Notes

- `hmac.compare_digest` prevents timing attacks; plain `==` does not.
- The key is loaded **once at import time** — the process fails to start with a clear
  `RuntimeError` if `DEVMCP_API_KEY` is unset.
- The key is **never** logged, never placed in `SYSTEM_PROMPT`, and never echoed in any
  response body.
- On Android the key lives in `SharedPreferences` (same security tier as `groq_api_key`).

---

## Verification Plan

### Server
- Start server **without** `DEVMCP_API_KEY` set → expect `RuntimeError` in terminal.
- Start with env var set → curl `/health` without header → expect HTTP 401.
- Curl `/health` with correct `X-DevMCP-Key` → expect 200.

### Android
- Open Settings → confirm three secret fields all present with show/hide toggle.
- Send a chat message → confirm server receives header (check `mcp_server.log`).
- Wrong key in app → server rejects with 401 → app surfaces an error toast.
