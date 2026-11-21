-- 기존 닉네임은 그대로 보존하고, 토큰 인증은 기존 refresh_tokens 행을 사용한다.
ALTER TABLE users MODIFY COLUMN nickname LONGTEXT NULL, DROP COLUMN auth_generation;
