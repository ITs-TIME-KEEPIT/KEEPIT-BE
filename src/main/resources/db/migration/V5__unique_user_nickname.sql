-- 기존 닉네임 원문을 보존하면서 새 요청과 동일한 앞뒤 공백 기준으로 비교한다.
-- 빈 닉네임과 NULL은 프로필 입력 전이므로 여러 회원에게 허용한다.
CREATE UNIQUE INDEX uk_users_nickname ON users
    ((CASE WHEN OCTET_LENGTH(TRIM(nickname)) > 0 THEN TRIM(nickname) ELSE NULL END));
