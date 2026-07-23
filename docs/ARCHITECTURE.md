# DevMCP Architecture

## Overview
DevMCP is an event-driven personal AI agent infrastructure built on AWS.
It consists of two agent layers connected via Kafka, with a Tauri desktop 
app as the primary interface and an Android app as a remote companion.

## Two-layer agent system

### Layer 1 — Reactive agent
- Runs on AWS EC2 (t3.small)
- FastAPI server handling all user commands in real time
- LLM loop: Z.ai GLM primary, Groq Llama fallback
- Consumes from Kafka commands topic
- Dispatches tool calls to local executor
- Publishes structured events to Kafka events topic

### Layer 2 — Proactive brain
- Background process on same EC2 instance
- Indexes files, code, emails, calendar, task logs, git commits
- Generates embeddings via OpenAI text-embedding-3-small
- Stores vectors in RDS PostgreSQL + pgvector
- Publishes context updates to Kafka context topic

## Kafka topics
| Topic    | Producer          | Consumer           | Purpose                        |
|----------|-------------------|--------------------|--------------------------------|
| commands | Desktop, Android  | Reactive agent     | User input                     |
| events   | Reactive agent    | Log panel, Dynamo  | Every agent action             |
| context  | Proactive brain   | Reactive agent     | RAG context updates            |
| status   | Reactive agent    | Desktop, Android   | Task progress                  |

## Storage
| Store         | Purpose                                      |
|---------------|----------------------------------------------|
| DynamoDB      | Tasks, event logs, conversations, preferences|
| RDS pgvector  | Vector embeddings for RAG memory             |
| S3            | Files, screenshots, artifacts                |

## Latency budgets
| Interaction                        | Target       |
|------------------------------------|--------------|
| Voice → Whisper text               | 300–500ms    |
| LLM first token                    | 500–800ms    |
| Full LLM response                  | 2–5s         |
| Tool call (local)                  | 50–200ms     |
| Playwright browser task            | 1–15s        |
| Kafka publish                      | 20–50ms      |
| DynamoDB read/write                | 5–15ms       |
| pgvector similarity search         | 50–150ms     |
| Full command → response (simple)   | 3–6s         |
| Full command → response (browser)  | 8–15s        |
