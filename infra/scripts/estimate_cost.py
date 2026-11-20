"""Calculate a reproducible USD baseline from the captured AWS Price List response."""

import json
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
prices = json.loads((ROOT / "review/pricing-seoul.json").read_text(encoding="utf-8-sig"))


def price(item, description=None):
    matches = {
        Decimal(row["USD"])
        for row in prices
        if row["Item"] == item
        and row["Begin"] == "0"
        and (description is None or description in row["Description"])
    }
    if len(matches) != 1:
        raise ValueError(f"Expected one starting unit rate for {item}: {matches}")
    return matches.pop()


hours = Decimal(730)
rows = [
    ("EC2 t3.micro", f"$ {price('EC2 t3.micro')}/hour × 730", price("EC2 t3.micro") * hours),
    ("EC2 gp3 8 GiB", f"$ {price('EBS gp3')}/GiB-month × 8", price("EBS gp3") * 8),
    ("Public IPv4, 1 address", "$0.005/hour × 730", Decimal("0.005") * hours),
    ("RDS db.t4g.micro, Single-AZ", f"$ {price('RDS db.t4g.micro', 'instance hour')}/hour × 730", price("RDS db.t4g.micro", "instance hour") * hours),
    ("RDS gp3 20 GiB", f"$ {price('RDS gp3')}/GiB-month × 20", price("RDS gp3") * 20),
    ("RDS-managed Secrets Manager, 1 secret", "$0.40/secret-month", Decimal("0.40")),
    ("S3 Standard example: 1 GB stored", f"$ {price('S3 Standard')}/GB-month × 1", price("S3 Standard")),
]
lines = [
    "# 서울 개발 환경 예상 비용",
    "",
    "조회 기준: 2026-11-17. USD, 월 730시간, On-Demand, 세금·크레딧 차감 전.",
    "EC2·RDS·EBS·S3 단가는 AWS Price List API의 서울 리전 응답을 사용했습니다.",
    "IPv4와 Secrets Manager 단가는 아래 공식 요금 페이지에서 확인했습니다.",
    "",
    "| 항목 | 계산 기준 | 월 USD |",
    "| --- | --- | ---: |",
]
lines.extend(f"| {name} | {basis} | ${cost:.4f} |" for name, basis, cost in rows)
total = sum((cost for _, _, cost in rows), Decimal(0))
lines += [
    f"| **기본 합계** | **S3 1GB 가정** | **${total:.2f}** |",
    "",
    "기본 합계는 비용 상한이 아닙니다. 크레딧 적용 여부·잔액·만료일은 이 조회로 확인하지 않았습니다.",
    "인터넷 송신, S3/API 요청, AZ 간 데이터 전송, 추가 로그·백업·스냅샷·비밀값 요청 요금은 별도입니다.",
    "EC2와 RDS는 같은 AZ로 배치하지만 장애·변경 이후 배치와 전송 비용을 재확인해야 합니다.",
    "EC2는 standard CPU credit 모드여서 크레딧 소진 시 성능이 제한됩니다.",
    "RDS T 계열은 CPU credit 추가 요금이 발생할 수 있어 지속 고부하에서는 이 예상치를 초과합니다.",
    "S3는 예약 용량·최소 인스턴스가 없으며 실제 저장량·버전·요청·전송량으로 과금합니다.",
    "S3의 삭제된 파일도 이전 버전은 최대 7일간 보관되어 용량에 포함됩니다.",
    "RDS 삭제 시 남는 최종 스냅샷·보존 백업은 별도 비용이 발생할 수 있습니다.",
    "",
    "단가 원본: [pricing-seoul.json](pricing-seoul.json)",
    "재계산: 저장소 루트에서 `python infra/scripts/estimate_cost.py`",
    "",
    "- [EC2](https://aws.amazon.com/ec2/pricing/on-demand/)",
    "- [EBS](https://aws.amazon.com/ebs/pricing/)",
    "- [RDS MySQL](https://aws.amazon.com/rds/mysql/pricing/)",
    "- [S3](https://aws.amazon.com/s3/pricing/)",
    "- [Public IPv4](https://aws.amazon.com/vpc/pricing/)",
    "- [Secrets Manager](https://aws.amazon.com/secrets-manager/pricing/)",
]
(ROOT / "review/cost-estimate.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Seoul baseline, 730h, S3 1 GB: USD {total:.2f}/month before tax and credits")
