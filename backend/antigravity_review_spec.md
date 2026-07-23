# Antigravity Task Review Workflow Specification

This document details the contract between the DevMCP Python backend, the Android app, and Antigravity AI for the new interactive diff review step in code-writing tasks.

## 1. Initial State
- The `write_code_task` tool creates a task JSON in `C:/DevMCP/inbox/` with `"status": "pending"`.
- It writes to `C:/DevMCP/trigger.flag` to wake Antigravity.

## 2. Antigravity Processing & Diff Generation
When Antigravity processes a pending task:
1. It moves the task file from `inbox/` to `processing/`.
2. It determines the code changes needed based on the task description and instructions.
3. **Crucially:** It does *not* write these changes to disk yet.
4. Instead, it updates the task JSON in `processing/` by adding a new field `diff_preview`. This field should contain either a unified diff against the existing file or the full proposed file content (for new files).
5. It updates the task `"status"` from `"in_progress"` to `"awaiting_approval"`.
6. It saves this JSON atomically to `processing/`.
7. Antigravity pauses work on this task (or exits the execution loop for it) until approval is granted.

## 3. Android UI Review
1. The Android app periodically polls `GET /task-status/{task_id}`.
2. When it sees `"status": "awaiting_approval"`, it displays a **Review Diff** button.
3. Clicking this button opens a modal showing the `diff_preview` text.
4. The user clicks **Approve** or **Reject**, which sends a request to `POST /task-approve/{task_id}` with payload `{"action": "approve"}` or `{"action": "reject"}`.

## 4. Backend Action
When `POST /task-approve/{task_id}` is called:
1. The backend updates the task JSON in `processing/`, setting `"status"` to `"approved"` or `"rejected"`.
2. The backend writes the current timestamp to `C:/DevMCP/trigger.flag` to wake Antigravity up again.

## 5. Antigravity Finalization
When Antigravity wakes up and scans the `processing/` folder:
- **If status is `"approved"`**: Antigravity proceeds to apply the proposed changes to the actual target file on disk. Once done, it updates `"status"` to `"done"`, and moves the task JSON to `C:/DevMCP/results/`.
- **If status is `"rejected"`**: Antigravity discards the changes, updates `"status"` to `"rejected"` (or logs the rejection), and moves the task JSON to `C:/DevMCP/results/`.
