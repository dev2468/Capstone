# Proposed Changes to Harden Command Execution

I agree with the allowlist provided — it covers all the standard package managers and tools you are likely to need, without exposing arbitrary shell command execution.

Here is the full diff of the proposed changes for both files.

## `api.py` Diff

```diff
--- api.py
+++ api.py
@@ -1,6 +1,7 @@
 from fastapi import FastAPI, HTTPException, Depends
 from fastapi.security import APIKeyHeader
 from pydantic import BaseModel
+import re
 import os
 import hmac
 import subprocess
@@ -62,6 +63,22 @@
 # ── Config ────────────────────────────────────────────────────
 COMMAND_TIMEOUT = 300
 AI_TIMEOUT = 300
+
+DANGEROUS_PATTERNS = re.compile(r'[;&`<>|]|\$[\(\{]')
+ALLOWED_COMMAND_PREFIXES = {
+    "git", "python", "pip", "uv", "npm", "node", "gradlew", 
+    "java", "adb", "dir", "type", "echo", "curl"
+}
+
+def is_command_allowed(command: str) -> tuple[bool, str]:
+    if DANGEROUS_PATTERNS.search(command):
+        return False, "Command contains forbidden character/sequence"
+    parts = command.strip().split()
+    if not parts:
+        return False, "Empty command"
+    prefix = parts[0].strip("'\"")
+    if prefix.lower() not in ALLOWED_COMMAND_PREFIXES:
+        return False, f"Command prefix '{prefix}' is not in the allowlist."
+    return True, ""
 
 # ── System Prompt ─────────────────────────────────────────────
 SYSTEM_PROMPT = """You are Dev's personal AI assistant running on his Windows laptop.
@@ -226,8 +243,14 @@
             if not command:
                 raise HTTPException(status_code=400, detail="No command provided")
+            
+            allowed, reason = is_command_allowed(command)
+            if not allowed:
+                log.warning(f"Blocked command attempt: '{command}' | Reason: {reason}")
+                raise HTTPException(status_code=403, detail=f"Command blocked by security policy. Reason: {reason}")
+                
             proc = subprocess.run(
-                ["powershell", "-Command", command],
+                ["powershell", "-ExecutionPolicy", "RemoteSigned", "-NonInteractive", "-NoProfile", "-Command", command],
                 capture_output=True,
                 text=True,
                 timeout=COMMAND_TIMEOUT
@@ -460,8 +483,14 @@
         elif name == "run_command":
             command = arguments.get("command", "").strip()
+            
+            allowed, reason = is_command_allowed(command)
+            if not allowed:
+                log.warning(f"Blocked command attempt: '{command}' | Reason: {reason}")
+                return f"Error: Command blocked by security policy. Reason: {reason}"
+                
             proc = subprocess.run(
-                ["powershell", "-Command", command],
+                ["powershell", "-ExecutionPolicy", "RemoteSigned", "-NonInteractive", "-NoProfile", "-Command", command],
                 capture_output=True, text=True, timeout=COMMAND_TIMEOUT
             )
```

## `server.py` Diff

```diff
--- server.py
+++ server.py
@@ -1,10 +1,33 @@
 from mcp.server import Server
 from mcp.server.stdio import stdio_server
 from mcp import types
 import asyncio
 import os
+import re
+import logging
 import subprocess
 import traceback
 
+logging.basicConfig(
+    level=logging.INFO,
+    format="%(asctime)s [%(levelname)s] %(message)s",
+    handlers=[
+        logging.FileHandler("mcp_server.log"),
+        logging.StreamHandler()
+    ]
+)
+log = logging.getLogger(__name__)
+
+DANGEROUS_PATTERNS = re.compile(r'[;&`<>|]|\$[\(\{]')
+ALLOWED_COMMAND_PREFIXES = {
+    "git", "python", "pip", "uv", "npm", "node", "gradlew", 
+    "java", "adb", "dir", "type", "echo", "curl"
+}
+
+def is_command_allowed(command: str) -> tuple[bool, str]:
+    if DANGEROUS_PATTERNS.search(command):
+        return False, "Command contains forbidden character/sequence"
+    parts = command.strip().split()
+    if not parts:
+        return False, "Empty command"
+    prefix = parts[0].strip("'\"")
+    if prefix.lower() not in ALLOWED_COMMAND_PREFIXES:
+        return False, f"Command prefix '{prefix}' is not in the allowlist."
+    return True, ""
+
 app = Server("Dev-MCP")
 
 def read_any_file(filepath):
@@ -114,8 +137,14 @@
             if not command:
                 return [types.TextContent(type="text", text="Error: no command provided")]
+                
+            allowed, reason = is_command_allowed(command)
+            if not allowed:
+                log.warning(f"Blocked command attempt: '{command}' | Reason: {reason}")
+                return [types.TextContent(type="text", text=f"Error: Command blocked by security policy. Reason: {reason}")]
+                
             result = subprocess.run(
-                ["powershell", "-Command", command],
+                ["powershell", "-ExecutionPolicy", "RemoteSigned", "-NonInteractive", "-NoProfile", "-Command", command],
                 capture_output=True,
                 text=True,
                 timeout=30
```

Please review the diff above. Once you confirm, I will apply the changes and execute the verification steps.
