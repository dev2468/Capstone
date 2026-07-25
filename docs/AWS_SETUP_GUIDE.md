# DevMCP AWS Setup & Provisioning Guide

## Overview
This step-by-step guide walks you through setting up the complete AWS cloud infrastructure for **DevMCP**, including budget alerts, MSK Serverless Kafka, EC2 host instance, IAM roles, and storage services.

---

## Step 1 — Set Up AWS Billing Budget & Alert

Prevent unexpected cloud charges before deploying resources.

1. Navigate to **AWS Console** → **Billing & Cost Management** → **Budgets**.
2. Click **Create budget** → Select **Custom budget (Cost budget)**.
3. Name the budget: `devmcp-monthly-budget`.
4. Set **Target amount**: `$30.00` / month.
5. Configure Alert Threshold: Trigger email alert at **80% ($24.00)** of budgeted amount.

---

## Step 2 — Provision AWS MSK Serverless Cluster

MSK Serverless hosts the 4 Kafka topics without needing cluster management.

1. Navigate to **AWS Console** → **Amazon MSK** → **Clusters** → **Create cluster**.
2. Select **Quick create** or **Custom create** → Choose **Serverless** mode.
3. Set **Cluster name**: `devmcp-kafka`.
4. Select your primary VPC and private subnets.
5. Click **Create cluster** (takes 10–15 minutes to provision).
6. Once active, view **Client information** to get your **Bootstrap Server Endpoint**:
   `boot-abc123.c1.kafka-serverless.ap-south-1.amazonaws.com:9098`

---

## Step 3 — Create MSK Kafka Topics

Create the 4 namespaced topics required for DevMCP.

```bash
# Using AWS MSK CLI / Console or Admin Client:
devmcp.commands
devmcp.events
devmcp.context
devmcp.status
```

*Partitioning Tip:* Set **Partitions** = `1` (or `3` for parallel scaling) and **Retention** = `7 days`.

---

## Step 4 — Create IAM Role for EC2 Instance

Grant your EC2 instance access to MSK Serverless via IAM SASL authentication.

1. Go to **AWS Console** → **IAM** → **Roles** → **Create role**.
2. Select **AWS Service** → **EC2**.
3. Attach policies:
   - `AmazonMSKFullAccess` (or custom policy for MSK topic publish/consume)
   - `AmazonDynamoDBFullAccess`
   - `AmazonS3FullAccess`
4. Name the role: `DevMCP-EC2-Role` and click **Create**.

---

## Step 5 — Launch EC2 Instance (Reactive Agent Host)

1. Navigate to **AWS Console** → **EC2** → **Launch Instance**.
2. Name: `devmcp-agent-server`.
3. AMI: **Ubuntu 22.04 LTS** or **Amazon Linux 2023**.
4. Instance Type:
   - `t2.micro` (AWS Free Tier eligible)
   - `t3.small` (Recommended for production performance, ~$15/month)
5. Under **IAM Instance Profile**, select `DevMCP-EC2-Role`.
6. Configure Security Group:
   - Inbound: SSH (Port 22, restricted to your IP), HTTP (Port 80/8000 for FastAPI).
   - Outbound: Allow all traffic.

---

## Step 6 — Provision Storage (DynamoDB & RDS pgvector)

### DynamoDB Tables
1. **`devmcp_tasks`**: Primary Key `task_id` (String). Stores task history.
2. **`devmcp_event_logs`**: Partition Key `task_id` (String), Sort Key `timestamp` (String).

### RDS PostgreSQL with pgvector (Optional / Brain Layer)
1. Navigate to **RDS** → **Create Database** → Select **PostgreSQL**.
2. Class: `db.t3.micro` (Free Tier eligible).
3. Connect to database and enable vector extension:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

---

## Step 7 — Configure EC2 Environment (`.env`)

SSH into your EC2 instance and set up your backend `.env`:

```env
# Server Configuration
PORT=8000
ENVIRONMENT=production

# Kafka / MSK Settings
KAFKA_BOOTSTRAP_SERVERS=boot-abc123.c1.kafka-serverless.ap-south-1.amazonaws.com:9098
KAFKA_SECURITY_PROTOCOL=SASL_SSL
KAFKA_SASL_MECHANISM=AWS_MSK_IAM

# AWS Credentials / Region
AWS_REGION=ap-south-1

# LLM & Embedding API Keys
GLM_API_KEY=your_glm_key
GROQ_API_KEY=your_groq_key
OPENAI_API_KEY=your_openai_key
```

---

## Cost Optimization Comparison Table

| Resource | Free Tier Limit | Estimated Monthly Cost | Optimization Tip |
|---|---|---|---|
| EC2 `t2.micro` | 750 hrs/month | $0.00 | Free for first 12 months |
| MSK Serverless | No free tier | $10.00–$15.00 | Pay per partition & storage used |
| RDS `t3.micro` | 750 hrs/month | $0.00 | Free for first 12 months |
| DynamoDB | 25 GB free | $0.00 | Stays free under capstone load |
| Data Transfer | 1 GB/month | $1.00–$2.00 | Negligible for JSON events |
| **Total** | — | **~$11.00–$17.00** | Covered easily by $100 credits |
