# DevMCP

An open source, self-hostable personal AI agent infrastructure. 
JARVIS for developers — voice-first, event-driven, and observable.

## What it does
- Accepts voice or text commands via a Tauri desktop app
- Executes tasks autonomously — terminal, files, browser, code, Google Workspace
- Maintains long-term memory via RAG on your personal context
- Learns your approval preferences over time
- Streams every agent action to a real-time log panel

## Architecture
Two-layer agent system:
- **Reactive agent** — real-time, handles your commands, runs on AWS EC2
- **Proactive brain** — background indexer, builds RAG memory from your files, code, emails, calendar

Full architecture doc → [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Tech stack
- Backend: FastAPI (Python) on AWS EC2
- Event backbone: AWS MSK Kafka
- Databases: DynamoDB + RDS PostgreSQL + pgvector
- Desktop: Tauri (Rust + React)
- Android: Kotlin + Jetpack Compose
- Browser automation: Playwright
- LLM: Z.ai GLM (primary), Groq Llama (fallback)
- Embeddings: OpenAI text-embedding-3-small

## Getting started

### Prerequisites
- Python 3.11+
- Node.js 18+
- Rust (for Tauri)
- Android Studio (for Android app)
- AWS account with MSK, DynamoDB, RDS, S3 access
- Docker + Docker Compose

### Setup
1. Clone the repo
   git clone https://github.com/yourusername/devmcp.git
   cd devmcp

2. Copy and fill environment variables
   cp .env.example .env
   # Edit .env with your actual keys and endpoints

3. Start with Docker Compose
   docker compose up

4. See individual module READMEs for detailed setup:
   - backend/README.md
   - desktop/README.md
   - android/README.md
   - local-executor/README.md

## Project structure
devmcp/
├── backend/          FastAPI reactive agent + API endpoints
├── android/          Kotlin companion app
├── desktop/          Tauri JARVIS desktop app
├── local-executor/   Tool executor, Playwright, file watcher
├── infra/            Docker Compose, AWS configs
├── docs/             Architecture, API contract, event schema
├── .env.example      All environment variables documented
└── README.md

## Contributing
This is a capstone research project. Contributions and forks welcome.
See docs/CONTRIBUTING.md for guidelines.

## License
MIT
