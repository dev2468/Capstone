set PATH=C:\Users\HP\Desktop\MCP\venv\Scripts;%PATH%
cd C:\Users\HP\Desktop\MCP
echo [%date% %time%] Task started >> task_debug.log
set DEVMCP_API_KEY=cKQ211NdP3kgFllcVkiaqe4X+Rrdch5xCLa+nFfoVwc=
set ZAI_API_KEY=3514fa3a80fc4f2ca2f074d25899de84.pqRFzbESgULpAIIu
call venv\Scripts\activate >> task_debug.log 2>&1
echo [%date% %time%] venv activated, launching python >> task_debug.log
python api.py >> task_debug.log 2>&1
echo [%date% %time%] python exited >> task_debug.log