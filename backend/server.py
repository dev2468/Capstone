from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp import types
import asyncio
import os
import re
import logging
import subprocess
import traceback
import tools

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.FileHandler(r"C:\Users\HP\Desktop\MCP\mcp_server.log"),
        logging.StreamHandler()
    ]
)
log = logging.getLogger(__name__)

app = Server("Dev-MCP")

@app.list_tools()
async def list_tools():
    return [
        types.Tool(
            name="list_files",
            description="List files in a folder on the laptop",
            inputSchema={
                "type": "object",
                "properties": {
                    "folder": {"type": "string"}
                },
                "required": ["folder"]
            }
        ),
        types.Tool(
            name="run_command",
            description="Runs any Windows PowerShell command and returns the output",
            inputSchema={
                "type": "object",
                "properties": {
                    "command": {"type": "string"}
                },
                "required": ["command"]
            }
        ),
        types.Tool(
            name="read_file",
            description="Reads contents of a file. Supports txt, py, js, json, csv, pdf, docx, xlsx, pptx, jpg, png and more",
            inputSchema={
                "type": "object",
                "properties": {
                    "filepath": {"type": "string"}
                },
                "required": ["filepath"]
            }
        ),
        types.Tool(
            name="write_file",
            description="Write text content to a file on the laptop. Allowed paths: C:\\DevMCP, C:\\Users\\HP\\Desktop\\MCP, C:\\Users\\HP\\Downloads",
            inputSchema={
                "type": "object",
                "properties": {
                    "filepath": {"type": "string"},
                    "content":  {"type": "string"}
                },
                "required": ["filepath", "content"]
            }
        )
    ]


@app.call_tool()
async def call_tools(name: str, arguments: dict):
    try:
        if name == "list_files":
            folder = arguments.get("folder", "")
            try:
                content = tools.list_files_impl(folder)
                return [types.TextContent(type="text", text=content)]
            except Exception as e:
                return [types.TextContent(type="text", text=f"Error: {str(e)}")]

        elif name == "run_command":
            command = arguments.get("command", "")
            try:
                output = await tools.run_command_impl(command, timeout=30)
                return [types.TextContent(type="text", text=output)]
            except ValueError as e:
                if "blocked by security policy" in str(e):
                    log.warning(f"Blocked command attempt: '{command}' | Reason: {str(e)}")
                return [types.TextContent(type="text", text=f"Error: {str(e)}")]

        elif name == "read_file":
            filepath = arguments.get("filepath", "")
            try:
                content = tools.read_file_impl(filepath)
                return [types.TextContent(type="text", text=content)]
            except Exception as e:
                return [types.TextContent(type="text", text=f"Error: {str(e)}")]

        elif name == "write_file":
            filepath = arguments.get("filepath", "")
            content = arguments.get("content", "")
            try:
                result = tools.write_file_impl(filepath, content)
                return [types.TextContent(type="text", text=result)]
            except ValueError as e:
                return [types.TextContent(type="text", text=f"Error: {str(e)}")]
            except Exception as e:
                return [types.TextContent(type="text", text=f"Write error: {str(e)}")]

        else:
            return [types.TextContent(type="text", text=f"Error: unknown tool: {name}")]

    except asyncio.TimeoutError:
        return [types.TextContent(type="text", text="Error: command timed out after 30 seconds")]
    except PermissionError:
        return [types.TextContent(type="text", text="Error: permission denied. Try running Claude Desktop as administrator")]
    except Exception as e:
        return [types.TextContent(type="text", text=f"Error: {str(e)}\n{traceback.format_exc()}")]


async def main():
    async with stdio_server() as (read, write):
        await app.run(read, write, app.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())