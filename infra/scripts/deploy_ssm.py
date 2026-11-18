"""Deploy the checked-out Compose configuration to the single dev EC2 via SSM."""

import argparse
import base64
import json
import re
import subprocess
import time
from pathlib import Path


def aws(*arguments: str) -> str:
    result = subprocess.run(
        ["aws", *arguments], check=True, text=True, capture_output=True
    )
    return result.stdout.strip()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", required=True)
    parser.add_argument("--image", required=True)
    args = parser.parse_args()

    if not re.fullmatch(
        r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/keepit-dev-app:[0-9a-f]{40}",
        args.image,
    ):
        parser.error("Image must be the ECR repository tagged with a commit SHA")

    instances = json.loads(
        aws(
            "ec2", "describe-instances", "--region", args.region,
            "--filters", "Name=tag:Name,Values=keepit-dev",
            "Name=instance-state-name,Values=running", "--output", "json",
        )
    )
    instance_ids = [
        instance["InstanceId"]
        for reservation in instances["Reservations"]
        for instance in reservation["Instances"]
    ]
    if len(instance_ids) != 1:
        raise RuntimeError(f"Expected one running keepit-dev instance, found {len(instance_ids)}")

    repo = Path(__file__).resolve().parents[2]
    compose = base64.b64encode((repo / "docker-compose.prod.yml").read_bytes()).decode()
    registry = args.image.split("/", 1)[0]
    commands = [
        "set -euo pipefail",
        "test -f /opt/keepit/.env",
        f"printf '%s' '{compose}' | base64 -d > /opt/keepit/docker-compose.prod.yml",
        f"printf '%s\\n' 'DOCKER_IMAGE={args.image}' > /opt/keepit/.image.env",
        "cd /opt/keepit",
        f"aws ecr get-login-password --region {args.region} | docker login --username AWS --password-stdin {registry}",
        "docker compose --env-file .image.env -f docker-compose.prod.yml config --quiet",
        "docker compose --env-file .image.env -f docker-compose.prod.yml pull",
        "docker compose --env-file .image.env -f docker-compose.prod.yml up -d --remove-orphans",
        "docker image prune -f",
    ]
    command_id = aws(
        "ssm", "send-command", "--region", args.region,
        "--instance-ids", instance_ids[0],
        "--document-name", "AWS-RunShellScript",
        "--comment", f"Deploy {args.image.rsplit(':', 1)[1]}",
        "--parameters", json.dumps({"commands": commands}),
        "--query", "Command.CommandId", "--output", "text",
    )
    print(f"SSM command {command_id} sent to {instance_ids[0]}", flush=True)

    result = None
    for _ in range(60):
        invocation = subprocess.run([
            "aws", "ssm", "get-command-invocation", "--region", args.region,
            "--command-id", command_id, "--instance-id", instance_ids[0],
            "--output", "json",
        ], check=False, text=True, capture_output=True)
        if invocation.returncode:
            if "InvocationDoesNotExist" not in invocation.stderr:
                raise RuntimeError(invocation.stderr.strip())
        else:
            result = json.loads(invocation.stdout)
            if result["Status"] not in ("Pending", "InProgress", "Delayed"):
                break
        time.sleep(10)
    if result is None or result["Status"] in ("Pending", "InProgress", "Delayed"):
        raise RuntimeError(f"SSM deployment timed out: {command_id}")
    print(result.get("StandardOutputContent", ""))
    print(result.get("StandardErrorContent", ""))
    if result["Status"] != "Success":
        raise RuntimeError(f"SSM deployment ended with {result['Status']}")


if __name__ == "__main__":
    main()
