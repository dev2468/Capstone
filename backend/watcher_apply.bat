@echo off
cd /d "C:\Users\HP\Desktop\MCP"
powershell -NoProfile -Command "agy --dangerously-skip-permissions -p \"$(Get-Content -Raw watcher_apply_prompt.txt)\"" > watcher_apply.log 2>&1
