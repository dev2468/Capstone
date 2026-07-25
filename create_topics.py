import boto3
from kafka.admin import KafkaAdminClient, NewTopic
from aws_msk_iam_sasl_signer import MSKAuthTokenProvider

BOOTSTRAP = "boot-hfrwzrof.c1.kafka-serverless.ap-south-1.amazonaws.com:9098"  # paste your actual endpoint here
REGION = "ap-south-1"

class MSKTokenProvider:
    def token(self):
        token, _ = MSKAuthTokenProvider.generate_auth_token(REGION)
        return token

admin = KafkaAdminClient(
    bootstrap_servers=BOOTSTRAP,
    security_protocol="SASL_SSL",
    sasl_mechanism="OAUTHBEARER",
    sasl_oauth_token_provider=MSKTokenProvider(),
)

topics = [
    NewTopic("devmcp.commands", num_partitions=3, replication_factor=1),
    NewTopic("devmcp.events",   num_partitions=3, replication_factor=1),
    NewTopic("devmcp.context",  num_partitions=3, replication_factor=1),
    NewTopic("devmcp.status",   num_partitions=3, replication_factor=1),
]

admin.create_topics(new_topics=topics, validate_only=False)
print("All 4 topics created successfully!")
admin.close()