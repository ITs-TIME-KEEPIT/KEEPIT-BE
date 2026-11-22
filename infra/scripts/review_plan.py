"""Check the review plan's safety boundaries without applying any resource changes.

Usage: terraform show -json review.tfplan | python ../../scripts/review_plan.py
"""

import json
import sys
from collections import Counter
from pathlib import Path

plan = json.load(sys.stdin)
resources = {
    item["address"]: item
    for item in plan["planned_values"]["root_module"]["resources"]
}


def value(address):
    return resources[address]["values"]


def check(condition, message):
    if not condition:
        raise SystemExit(f"REVIEW FAILED: {message}")


changes = [item for item in plan["resource_changes"] if item["mode"] == "managed"]
check(all(item["change"]["actions"] == ["create"] for item in changes), "Expected a new environment with create-only actions")
check(plan["planned_values"]["outputs"]["deployment_account"]["value"] == "781953768324", "Unexpected deployment account")
db = value("aws_db_instance.main")
app = value("aws_instance.app")
check(not db["publicly_accessible"], "RDS must not be public")
check(db["storage_encrypted"] and db["manage_master_user_password"], "RDS encryption and managed password are required")
check(db.get("password") is None and db.get("password_wo") is None, "Do not put a DB password in this plan")
check(db["deletion_protection"] and not db["skip_final_snapshot"], "Database deletion safeguards are required")
check(not db["multi_az"] and db["allocated_storage"] == 20, "Review minimum RDS capacity")
check(db["instance_class"] == "db.t4g.micro", "Review RDS instance cost")
check(db["availability_zone"] == value("aws_subnet.public")["availability_zone"], "EC2 and RDS must start in the same AZ")
check(app["instance_type"] == "t3.micro" and app["root_block_device"][0]["volume_size"] == 20, "Review EC2 capacity and disk cost")
check(app["root_block_device"][0]["encrypted"], "EC2 EBS must be encrypted")
check(app["credit_specification"][0]["cpu_credits"] == "standard", "Prevent unreviewed EC2 surplus credit charges")
check(app["metadata_options"][0]["http_tokens"] == "required" and app["metadata_options"][0]["http_put_response_hop_limit"] == 2, "Container IAM credentials must use IMDSv2")
ingress = [item for item in resources.values() if item["type"] == "aws_vpc_security_group_ingress_rule"]
check(len(ingress) == 3, "Expected two HTTP rules and one internal MySQL rule")
check({item["values"]["from_port"] for item in ingress} == {80, 8080, 3306}, "Expected dev HTTP on ports 80 and 8080 and internal MySQL on 3306")
mysql = value("aws_vpc_security_group_ingress_rule.mysql")
check(mysql.get("cidr_ipv4") is None, "MySQL ingress must reference a security group, not a CIDR")
public_access = value("aws_s3_bucket_public_access_block.files")
check(all(public_access[key] for key in ("block_public_acls", "block_public_policy", "ignore_public_acls", "restrict_public_buckets")), "S3 public access must be blocked")
check(value("aws_s3_bucket_versioning.files")["versioning_configuration"][0]["status"] == "Enabled", "S3 recovery versioning is required")
check(not value("aws_s3_bucket.files")["force_destroy"], "S3 must not delete objects automatically on destroy")
check(not any(item["type"] in {"aws_nat_gateway", "aws_lb", "aws_eip"} for item in resources.values()), "Unreviewed fixed-cost network services")
check(len([item for item in resources.values() if item["type"] == "aws_route"]) == 1, "Only the public subnet may have an internet route")

counts = Counter(item["type"] for item in changes)
lines = [
    "# Terraform 변경 계획 검토",
    "",
    "검증일: 2026-11-17. 실제 계정 데이터 조회 후 생성한 plan을 분석했습니다.",
    f"계획: **{len(changes)}개 생성, 기존 리소스 변경 0개, 삭제 0개**.",
    "**apply는 실행하지 않았습니다.** 데이터 조회와 로컬 파일 작성만 수행했습니다.",
    "",
    "- 계정: `781953768324`; 리전: `ap-northeast-2`",
    f"- EC2·RDS 가용 영역: `{db['availability_zone']}`",
    f"- 검토 AMI: `{app['ami']}` (Canonical Ubuntu 24.04, x86_64)",
    "- 위 스크립트의 생성 전용·계정·DB 비공개/암호화·포트·S3 접근·고정비 서비스 검사가 통과했습니다.",
    "- TCP 80·8080은 전체 IPv4에 열리는 개발용 HTTP 포트입니다. TLS 설정 전 실제 사용자 인증·개인정보를 전송하지 않습니다.",
    "- 현재 검증은 생성 API 권한, 실제 EC2 부팅·패키지 설치, DB 접속, 앱 실행을 보장하지 않습니다.",
    "- 승인 후 최신 plan을 다시 검토하고 apply합니다. 이 문서는 특정 저장된 plan에 대한 배포 승인이 아닙니다.",
    "",
    "| 리소스 종류 | 생성 수 |",
    "| --- | ---: |",
]
lines.extend(f"| `{kind}` | {count} |" for kind, count in sorted(counts.items()))
destination = Path(__file__).resolve().parents[1] / "review/plan-summary.md"
destination.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"PASS: account, create-only plan, network isolation, encryption and cost boundaries; {len(changes)} creates")
