@echo off
cd /d "C:\Users\HP\Desktop\MCP"
powershell -NoProfile -Command "agy -p \"$(Get-Content -Raw watcher_draft_prompt.txt)\"" > watcher_draft.log 2>&1
