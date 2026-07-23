"""
DevMCP Inbox Watcher
====================
Runs as a background process on Windows login.
Polls C:/DevMCP/trigger.flag every 10 seconds.
On mtime change, fires `agy` in a thread (non-blocking).
Supports up to MAX_WORKERS concurrent agy instances.
"""

import os
import time
import json
import subprocess
import logging
import threading
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

# ── Config ─────────────────────────────────────────────────────
TRIGGER_FLAG  = r"C:\DevMCP\trigger.flag"
INBOX_DIR     = r"C:\DevMCP\inbox"
PROCESSING_DIR= r"C:\DevMCP\processing"
POLL_INTERVAL = 10          # seconds between checks
MAX_WORKERS   = 3           # max parallel agy runs
AGY_PROMPT    = "Check DevMCP inbox — process any pending tasks in C:/DevMCP/inbox/"
LOG_FILE      = r"C:\DevMCP\logs\watcher.log"

# ── Logging ─────────────────────────────────────────────────────
os.makedirs(os.path.dirname(LOG_FILE), exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler()
    ]
)
log = logging.getLogger(__name__)


def update_task_status(task_id: str, new_status: str, extra: dict = None):
    """Update a task JSON file with a new status and append to status_history."""
    for folder in [INBOX_DIR, PROCESSING_DIR]:
        path = os.path.join(folder, f"{task_id}.json")
        if os.path.exists(path):
            try:
                with open(path, encoding="utf-8") as f:
                    task = json.load(f)
                task["status"] = new_status
                history = task.get("status_history", [])
                history.append({"status": new_status, "at": datetime.now().isoformat()})
                task["status_history"] = history
                if extra:
                    task.update(extra)
                tmp = path + ".tmp"
                with open(tmp, "w", encoding="utf-8") as f:
                    json.dump(task, f, indent=2)
                os.replace(tmp, path)
                log.info("Task %s → %s", task_id, new_status)
            except Exception as e:
                log.warning("Could not update task %s status: %s", task_id, e)
            return


def run_agy(prompt: str) -> None:
    """Fire agy CLI in a thread. Updates task statuses before/after."""
    tid = threading.get_ident()
    log.info("[thread-%s] Triggering Antigravity CLI...", tid)
    try:
        result = subprocess.run(
            ["agy", "--dangerously-skip-permissions", "-p", prompt],
            capture_output=True,
            text=True,
            timeout=300   # 5 min max per task batch
        )
        if result.returncode == 0:
            log.info("[thread-%s] agy completed successfully.", tid)
        else:
            log.error("[thread-%s] agy exited with code %d: %s", tid, result.returncode, result.stderr[:500])
    except FileNotFoundError:
        log.error("agy not found in PATH. Is Antigravity installed?")
    except subprocess.TimeoutExpired:
        log.warning("[thread-%s] agy timed out after 5 minutes.", tid)
    except Exception as e:
        log.error("[thread-%s] agy call failed: %s", tid, e)


def main():
    log.info("DevMCP Inbox Watcher started. Polling every %ds | max_workers=%d", POLL_INTERVAL, MAX_WORKERS)
    executor = ThreadPoolExecutor(max_workers=MAX_WORKERS)
    last_mtime = None
    active_futures = []

    while True:
        # Clean up completed futures
        active_futures = [f for f in active_futures if not f.done()]

        if os.path.exists(TRIGGER_FLAG):
            current_mtime = os.path.getmtime(TRIGGER_FLAG)
            if current_mtime != last_mtime:
                last_mtime = current_mtime
                if len(active_futures) >= MAX_WORKERS:
                    log.warning("All %d workers busy — trigger queued until a slot frees.", MAX_WORKERS)
                else:
                    log.info("trigger.flag detected (mtime=%s) — dispatching agy [active=%d/%d]",
                             current_mtime, len(active_futures), MAX_WORKERS)
                    future = executor.submit(run_agy, AGY_PROMPT)
                    active_futures.append(future)
                    # After agy, re-read mtime
                    if os.path.exists(TRIGGER_FLAG):
                        last_mtime = os.path.getmtime(TRIGGER_FLAG)
        else:
            if last_mtime is not None:
                log.info("trigger.flag removed — watcher re-armed.")
                last_mtime = None

        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
