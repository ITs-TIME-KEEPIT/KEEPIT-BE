-- 기존 회원과 기존 JWT는 최초 세대 0으로 시작하고, 탈퇴 후 복구 시 세대가 증가한다.
ALTER TABLE users ADD COLUMN auth_generation BIGINT NOT NULL DEFAULT 0;
