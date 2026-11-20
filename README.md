# KEEPIT Backend

## Tech Stack

| 구분 | 기술 |
| --- | --- |
| Framework | Spring Boot 4.1.1 |
| Language | Java 17 |
| Build | Gradle 8.14.2 (Wrapper) |
| Database | MySQL 8.4 LTS, Spring Data JPA |
| DB Migration | Flyway |
| Auth | Spring Security + JWT (jjwt 0.13), Kakao Login |
| API Docs | springdoc-openapi 3.x (Swagger UI) |
| File Storage | AWS S3 (AWS SDK for Java v2) — 보류, 코드 주석 처리 |
| Infra | Docker, Docker Compose, 개발용 AWS EC2 / RDS / S3 / ECR |
| CI/CD | GitHub Actions |
| Code Review | CodeRabbit |
| P1 (선택) | Redis, PG 결제, FCM |

## 로컬 실행

로컬 DB는 **Docker Compose로 띄우는 MySQL 8.4**로 통일합니다. 팀원 모두 같은 버전을 쓰기 위해 MySQL을 PC에 직접 설치하지 않습니다.

**사전 준비:** Docker (Docker Desktop 또는 WSL2 + Docker Engine)

```bash
# 1. MySQL 8.4 실행 (최초 실행 시 keepit DB / 계정 자동 생성, Redis 필요 시 --profile redis 추가)
docker compose up -d

# 2. 애플리케이션 실행 (local 프로필)
./gradlew bootRun
```

- 기본 접속 정보(`localhost:3306`, DB `keepit`, 계정 `keepit`/`keepit`)는 `docker-compose.yml`과 `application-local.yml`에 동일하게 들어 있어 별도 설정 없이 바로 연결됩니다.
- 값을 바꾸고 싶다면 `.env.example`을 `.env`로 복사해 수정합니다. `.env`는 Docker Compose만 읽으므로, 같은 값을 IDE 실행 환경변수(`DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`)에도 넣어야 합니다.
- DB 중지: `docker compose stop` / 데이터까지 초기화: `docker compose down -v`

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/actuator/health

## 빌드 / 테스트

```bash
./gradlew build            # 빌드 + 테스트 (테스트는 Testcontainers 사용 → Docker 필요)
./gradlew build -x test    # 테스트 제외 빌드
./gradlew test --tests "com.keepit.SomeTest"   # 단일 테스트
```

## 프로젝트 구조

```
src/main/java/com/keepit
├── KeepitApplication.java
└── global
    ├── config          # Security, Swagger, JPA 설정 (S3는 보류)
    ├── security/jwt    # JWT 발급/검증, 인증 필터, EntryPoint
    ├── oauth/kakao     # 카카오 로그인 API 클라이언트
    └── s3              # S3 업로드/삭제 서비스 (AWS 보류 - 주석 처리)
src/main/resources
├── application.yml          # 공통 설정
├── application-local.yml    # 로컬 (docker compose MySQL)
├── application-prod.yml     # 운영 (DB 연결은 보류)
└── db/migration             # Flyway 마이그레이션 (V1__xxx.sql)
```

## AWS

개발용 AWS 인프라와 ECR·SSM 자동 배포 구성은 [infra/README.md](infra/README.md)에 정리되어 있습니다. Terraform 적용과 서버 환경변수 준비 후 PR이 `develop`에 병합되면 GitHub Actions가 개발 EC2에 배포합니다.
개발 EC2는 `docker-compose.dev.yml`과 `dev` 프로필을 사용합니다. `docker-compose.prod.yml`은 향후 운영 배포용이며 현재 자동 배포 트리거는 없습니다.

개발 EC2의 `/opt/keepit/.env`에 RDS 연결 정보와 `AWS_REGION`, `AWS_S3_BUCKET`을 지정합니다. S3 인증에는 EC2 IAM 역할을 사용합니다.

## 협업 컨벤션

브랜치·커밋·PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고해 주세요. clone 후 `git config core.hooksPath .githooks`를 실행하면 커밋 메시지 형식이 로컬에서 검사됩니다.
