"""Deploy the checked-out Compose configuration to the single dev EC2 via SSM."""

import argparse
import base64
import json
import os
import re
import subprocess
import time
from pathlib import Path


DOTENV_KEY = "alias/keepit-dev-deploy-env"
DOTENV_CONTEXT = "Application=keepit-dev"
REQUIRED_ENV = (
    "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "AWS_REGION", "AWS_S3_BUCKET",
)
OPTIONAL_ENV = ("KAKAO_APP_ID",)


def aws(*arguments: str, input_text: str | None = None) -> str:
    result = subprocess.run(
        ["aws", *arguments], input=input_text, check=True, encoding="utf-8", capture_output=True
    )
    return result.stdout.strip()


def build_dotenv() -> str:
    # 개별 Secret을 읽고 하위 프로세스의 환경에 노출되지 않도록 제거한다.
    entries = {name: os.environ.pop(f"KEEPIT_ENV_{name}", "") for name in REQUIRED_ENV + OPTIONAL_ENV}
    missing = [name for name in REQUIRED_ENV if not entries[name].strip()]
    if missing:
        raise ValueError("Missing GitHub repository secrets: " + ", ".join(missing))
    if any("\x00" in value for value in entries.values()):
        raise ValueError("GitHub repository secrets cannot contain null characters")

    # Compose가 따옴표, 공백, 달러 기호를 값 그대로 읽도록 이스케이프한다.
    lines = [
        f"{name}=" + json.dumps(value, ensure_ascii=False).replace("$", "$$")
        for name, value in entries.items() if value
    ]
    dotenv = "\n".join(lines) + "\n"
    if len(dotenv.encode("utf-8")) > 4096:
        raise ValueError("The generated dotenv file exceeds the KMS limit of 4096 UTF-8 bytes")
    return dotenv


def encrypt_dotenv(region: str, dotenv: str) -> str:
    # 평문은 러너의 표준 입력으로만 AWS CLI에 전달하고, SSM에는 암호문만 보낸다.
    ciphertext = aws(
        "kms", "encrypt", "--region", region, "--key-id", DOTENV_KEY,
        "--encryption-context", DOTENV_CONTEXT,
        "--plaintext", "fileb:///dev/stdin",
        "--query", "CiphertextBlob", "--output", "text",
        input_text=dotenv,
    )
    if not re.fullmatch(r"[A-Za-z0-9+/]+={0,2}", ciphertext):
        raise RuntimeError("KMS did not return a base64 ciphertext")
    return ciphertext


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--region", required=True)
    parser.add_argument("--image", required=True)
    args = parser.parse_args()

    # 배포 명령에서 리전과 이미지 주소를 안전하게 사용할 수 있는 형태인지 검사한다.
    if not re.fullmatch(r"[a-z]{2}(?:-[a-z0-9]+)+-[0-9]+", args.region):
        parser.error("Region must be an AWS region name")
    if not re.fullmatch(
        r"[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/keepit-dev-app:[0-9a-f]{40}",
        args.image,
    ):
        parser.error("Image must be the ECR repository tagged with a commit SHA")

    # 빈 GitHub Secret을 주입하면 기존 .env가 손상되므로 서버 접속 전에 실패시킨다.
    try:
        dotenv = build_dotenv()
    except ValueError as error:
        parser.error(str(error))
    ciphertext = encrypt_dotenv(args.region, dotenv)

    # 현재 실행 중인 개발 EC2 한 대만 배포 대상으로 지정한다.
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
    compose = base64.b64encode((repo / "docker-compose.dev.yml").read_bytes()).decode()
    registry = args.image.split("/", 1)[0]
    commands = [
        "#!/bin/bash",
        "set -euo pipefail",
        # 같은 서버에서 여러 SSM 배포가 동시에 환경 파일을 교체하지 않도록 잠근다.
        "cd /opt/keepit",
        "exec 9>/run/lock/keepit-dev-deploy.lock",
        "flock -n 9",
        f"printf '%s' '{compose}' | base64 -d > /opt/keepit/docker-compose.dev.yml",
        f"printf '%s\\n' 'DOCKER_IMAGE={args.image}' > /opt/keepit/.image.env",
        # EC2 역할로 복호화하고 기존 .env를 보존한 상태에서 Compose 설정을 검사한다.
        "umask 077",
        "dotenv_tmp=$(mktemp /opt/keepit/.env.XXXXXX)",
        "trap 'rm -f -- \"$dotenv_tmp\"' EXIT",
        f"aws kms decrypt --region {args.region} --key-id {DOTENV_KEY} "
        f"--encryption-context {DOTENV_CONTEXT} --cli-binary-format base64 "
        f"--ciphertext-blob '{ciphertext}' --query Plaintext --output text "
        '| base64 -d > "$dotenv_tmp"',
        'test -s "$dotenv_tmp"',
        'if ! KEEPIT_APP_ENV_FILE="$dotenv_tmp" docker compose --env-file .image.env '
        '-f docker-compose.dev.yml config --quiet >/dev/null 2>&1; then '
        'echo "Invalid deployment environment or Compose configuration." >&2; exit 1; fi',
        # 이미지 다운로드가 성공한 뒤에만 새 환경 파일을 활성화한다.
        f"aws ecr get-login-password --region {args.region} | docker login --username AWS --password-stdin {registry}",
        # 이전 배포가 실패해 남은 이미지 때문에 새 이미지를 받지 못하는 일이 없도록, 실행 중인 컨테이너가 쓰지 않는 이미지를 먼저 지운다.
        "docker image prune -af",
        'KEEPIT_APP_ENV_FILE="$dotenv_tmp" docker compose --env-file .image.env -f docker-compose.dev.yml pull',
        'mv -fT -- "$dotenv_tmp" .env',
        "docker compose --env-file .image.env -f docker-compose.dev.yml up -d --remove-orphans",
        r'''for ((attempt = 1; attempt <= 60; attempt++)); do
  if health=$(curl --fail --silent --max-time 2 --write-out '\n%{http_code}' http://127.0.0.1:8080/actuator/health) &&
     [[ "$health" == *$'\n200' ]] &&
     python3 -c 'import json, sys; sys.exit(json.load(sys.stdin).get("status") != "UP")' \
       <<< "${health%$'\n'*}" 2>/dev/null; then
    echo "Spring Boot health check passed."
    break
  fi
  if (( attempt == 60 )); then
    echo "Spring Boot did not report UP on port 8080." >&2
    docker compose --env-file .image.env -f docker-compose.dev.yml ps
    exit 1
  fi
  sleep 2
done''',
        # 새 컨테이너가 뜬 뒤에는 교체된 이전 버전 이미지까지 지워 다음 배포 공간을 확보한다.
        "docker image prune -af",
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
