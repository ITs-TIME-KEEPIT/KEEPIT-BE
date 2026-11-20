# Terraform 변경 계획 검토

검증일: 2026-11-17. 실제 계정 데이터 조회 후 생성한 plan을 분석했습니다.
계획: **31개 생성, 기존 리소스 변경 0개, 삭제 0개**.
**apply는 실행하지 않았습니다.** 데이터 조회와 로컬 파일 작성만 수행했습니다.

- 계정: `781953768324`; 리전: `ap-northeast-2`
- EC2·RDS 가용 영역: `ap-northeast-2a`
- 검토 AMI: `ami-05b829d75c9f9183e` (Canonical Ubuntu 24.04, x86_64)
- 위 스크립트의 생성 전용·계정·DB 비공개/암호화·포트·S3 접근·고정비 서비스 검사가 통과했습니다.
- public HTTP 80은 전체 IPv4에 열리는 개발용 계획입니다. TLS 설정 전 실제 사용자 인증·개인정보를 전송하지 않습니다.
- 현재 검증은 생성 API 권한, 실제 EC2 부팅·패키지 설치, DB 접속, 앱 실행을 보장하지 않습니다.
- 승인 후 최신 plan을 다시 검토하고 apply합니다. 이 문서는 특정 저장된 plan에 대한 배포 승인이 아닙니다.

| 리소스 종류 | 생성 수 |
| --- | ---: |
| `aws_db_instance` | 1 |
| `aws_db_parameter_group` | 1 |
| `aws_db_subnet_group` | 1 |
| `aws_iam_instance_profile` | 1 |
| `aws_iam_role` | 1 |
| `aws_iam_role_policy` | 1 |
| `aws_iam_role_policy_attachment` | 1 |
| `aws_instance` | 1 |
| `aws_internet_gateway` | 1 |
| `aws_route` | 1 |
| `aws_route_table` | 2 |
| `aws_route_table_association` | 3 |
| `aws_s3_bucket` | 1 |
| `aws_s3_bucket_lifecycle_configuration` | 1 |
| `aws_s3_bucket_ownership_controls` | 1 |
| `aws_s3_bucket_policy` | 1 |
| `aws_s3_bucket_public_access_block` | 1 |
| `aws_s3_bucket_server_side_encryption_configuration` | 1 |
| `aws_s3_bucket_versioning` | 1 |
| `aws_security_group` | 2 |
| `aws_subnet` | 3 |
| `aws_vpc` | 1 |
| `aws_vpc_security_group_egress_rule` | 1 |
| `aws_vpc_security_group_ingress_rule` | 2 |
