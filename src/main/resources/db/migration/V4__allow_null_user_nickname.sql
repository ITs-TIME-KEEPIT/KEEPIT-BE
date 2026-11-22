-- 프로필 입력 전에도 카카오 회원번호로 가입을 이어갈 수 있게 한다.
ALTER TABLE users MODIFY nickname VARCHAR(20) NULL;
