# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Stack

Spring Boot 4.1.1 / Java 17 / Gradle 8.14.2 wrapper / MySQL 8.4 + JPA + Flyway / Spring Security + JWT (jjwt) / Kakao Login / springdoc-openapi 3.x / AWS S3 (SDK v2, on hold). Redis, FCM, PG payment are optional (P1) and commented out in `build.gradle`.

## Commands

- Build without tests: `./gradlew build -x test`
- Full build + tests: `./gradlew build` (tests use Testcontainers MySQL 8.4 → Docker required)
- Single test: `./gradlew test --tests "com.keepit.SomeTest"`
- Run locally: `docker compose up -d` (MySQL) then `./gradlew bootRun` (profile `local` by default). The team standard for the local DB is the compose `mysql:8.4` container only — don't suggest native MySQL installs or H2 for local runs.

## Architecture notes

- Base package `com.keepit`; cross-cutting code lives in `global/` (config, security/jwt, oauth/kakao, s3). Domain features should go in their own packages beside `global`.
- Schema is owned by Flyway (`src/main/resources/db/migration`, `V{n}__desc.sql`); JPA runs with `ddl-auto: validate`. Never edit an already-applied migration.
- `JwtAuthenticationFilter` is intentionally not a Spring bean — it is instantiated in `SecurityConfig` to avoid double registration as a servlet filter. Public endpoints are listed in `SecurityConfig.PUBLIC_URLS`.
- Config is bound via `@ConfigurationProperties` records (`JwtProperties`, `KakaoProperties`) picked up by `@ConfigurationPropertiesScan`. Secrets come from env vars (see `.env.example`).
- AWS (EC2 deploy, RDS, S3) is on hold: all related code is commented out and tagged `[AWS 보류]` (S3 classes, AWS SDK deps, `aws:`/RDS config, `docs/deploy/cd.yml` draft workflow). Don't re-enable it unless asked; search for `[AWS 보류]` to find every spot.

## Git / collaboration conventions

- Branches: `main` (release only), `develop` (integration). Work branches are cut from the latest `develop` as `feature|fix|refactor|docs/{issue}-{요약}` where `{issue}` is the GitHub issue number; one branch resolves one issue. Never push directly to `main`/`develop` — open a PR into `develop`; `develop` → `main` only at release.
- Commit message: `{type}: {작업 내용}` — type in lowercase English (`feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `style`, `build`, `ci`), description in Korean, no trailing period. One logical change per commit; don't mix behavior changes with large reformatting.
- Schema changes ship Flyway migration + Entity + related tests in the same PR.
- Never commit build output, personal IDE settings, `.env`, or real API keys / JWT secrets / DB passwords.
- Every PR gets a CodeRabbit review (`.coderabbit.yaml`); address its suggestions or reply with a reason, request re-review after fixes, and merge only after major points are resolved.
