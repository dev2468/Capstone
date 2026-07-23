# Harden run_command Tool

This plan details the steps to harden the `run_command` tool in both `api.py` and `server.py` to prevent arbitrary command execution and restrict operations to a known safe set.

## User Review Required

> [!WARNING]  
> Please review the `Open Questions` section below, specifically regarding which command prefixes you actually use. We need to finalize the `ALLOWED_COMMAND_PREFIXES` list before executing the changes.

## Open Questions

1. **Command Allowlist**: The current proposed allowlist of command prefixes is: `["git", "python", "npm", "gradlew", "dir", "type", "adb"]`. 
   **Which of these do you actually use, and are there any others (e.g., `pip`, `node`, `java`, `uv`) that should be added?**
2. **Execution Policy**: Adding `-ExecutionPolicy Restricted` to PowerShell prevents it from running `.ps1` script files, but it still allows running individual commands and executables. Is this the exact behavior you want?

## Proposed Changes

### Configuration and Validation Logic (Both files)

We will introduce the following constants and validation function in both `api.py` and `server.py` (or shared if they were consolidated, but for now duplicated as requested):

```python
import logging

DISALLOWED_CHARS = [';', '|', '&', '`', '$(', ')', '<', '>']
ALLOWED_COMMAND_PREFIXES = ["git", "python", "npm", "gradlew", "dir", "type", "adb"]

def is_command_allowed(command: str) -> tuple[bool, str]:
    # 1. Check for disallowed characters
    for char in DISALLOWED_CHARS:
        if char in command:
            return False, f"Command contains forbidden character/sequence: {char}"
    
    # 2. Check prefix
    parts = command.strip().split()
    if not parts:
        return False, "Empty command"
    
    prefix = parts[0].strip("'\"")
    if prefix.lower() not in ALLOWED_COMMAND_PREFIXES:
        return False, f"Command prefix '{prefix}' is not in the allowlist."
    
    return True, ""
```

### Component: `api.py`

#### [MODIFY] [api.py](file:///C:/Users/HP/Desktop/MCP/api.py)
- Integrate `is_command_allowed` into `call_tool` and `execute_tool`.
- Log blocked attempts to `mcp_server.log` at WARNING level.
- Update the `subprocess.run` call to include `-ExecutionPolicy Restricted`.

```python
# Inside run_command block:
allowed, reason = is_command_allowed(command)
if not allowed:
    log.warning(f"Blocked command attempt: '{command}' | Reason: {reason}")
    return f"Error: Command blocked by security policy. Reason: {reason}"

proc = subprocess.run(
    ["powershell", "-ExecutionPolicy", "Restricted", "-Command", command],
    capture_output=True, text=True, timeout=COMMAND_TIMEOUT
)
```

### Component: `server.py`

#### [MODIFY] [server.py](file:///C:/Users/HP/Desktop/MCP/server.py)
- Add standard logging setup to append to `mcp_server.log` if it's not already doing so (currently it doesn't log to file).
- Apply the same validation logic and subprocess modification as in `api.py`.

## Verification Plan

### Automated Tests
- N/A (No automated test suite provided).

### Manual Verification
- We will try to execute commands with forbidden characters (e.g., `git status; echo hacked`). They should be blocked and logged as warnings.
- We will try to execute a command not in the allowlist (e.g., `whoami`). It should be blocked and logged.
- We will execute a valid command (e.g., `git status`). It should run successfully.
- We will check `mcp_server.log` to verify the WARNING entries are correctly formatted with the timestamp and command text.
