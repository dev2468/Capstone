"""
DevMCP Kafka Topic Creation Script
Uses confluent-kafka / kafka-python with AWS MSK IAM authentication to automatically create required topics.
"""

import os
from kafka.admin import KafkaAdminClient, NewTopic
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider

# Configuration
BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "YOUR_MSK_BOOTSTRAP_SERVER:9098")
REGION = os.getenv("AWS_REGION", "ap-south-1")

TOPICS_TO_CREATE = [
    {"name": "devmcp.commands", "num_partitions": 1, "replication_factor": 1},
    {"name": "devmcp.events", "num_partitions": 1, "replication_factor": 1},
    {"name": "devmcp.context", "num_partitions": 1, "replication_factor": 1},
    {"name": "devmcp.status", "num_partitions": 1, "replication_factor": 1},
]

class TokenProvider:
    def __call__(self):
        token, _ = MSKAuthTokenProvider.generate_auth_token(REGION)
        return token

def create_topics():
    print(f"Connecting to MSK Serverless cluster at {BOOTSTRAP_SERVERS}...")
    
    tp = TokenProvider()
    admin_client = KafkaAdminClient(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        security_protocol="SASL_SSL",
        sasl_mechanism="OAUTHBEARER",
        sasl_oauth_token_provider=tp,
    )

    topic_list = []
    for topic in TOPICS_TO_CREATE:
        topic_list.append(
            NewTopic(
                name=topic["name"],
                num_partitions=topic["num_partitions"],
                replication_factor=topic["replication_factor"]
            )
        )

    try:
        admin_client.create_topics(new_topics=topic_list, validate_only=False)
        print("Successfully created MSK topics:")
        for t in TOPICS_TO_CREATE:
            print(f"  - {t['name']}")
    except Exception as e:
        print(f"Topic creation result/error: {e}")

if __name__ == "__main__":
    create_topics()
