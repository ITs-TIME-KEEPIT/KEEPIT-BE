# 카카오 로그인과 프로필 등록

## 모바일 로그인

앱이 카카오 모바일 SDK로 로그인한 뒤 발급받은 카카오 액세스 토큰을 HTTPS로 백엔드에 전달한다. 앱이 로그인을 취소하면 백엔드 요청을 보내지 않는다.

```http
POST /api/v1/auth/kakao/login
Authorization: Bearer <카카오 SDK 액세스 토큰>
```

`Authorization: <카카오 SDK 액세스 토큰>`처럼 `Bearer ` 접두사를 생략할 수도 있다. 요청 본문은 비워 둔다. `Authorization` 헤더가 없거나 형식이 잘못되면 HTTP 400과 `AUTH_400_1`을 반환한다. 예전 형식대로 JSON 본문에만 `accessToken`을 보낸 요청도 이 오류를 반환한다.

Swagger UI에서는 **Authorize**의 `kakaoBearerAuth`에 SDK 토큰 값만 입력한다. Swagger UI가 헤더에 `Bearer ` 접두사를 붙인다.

백엔드는 카카오 토큰 정보 API로 남은 유효 시간, 회원번호 및 발급 앱 ID를 확인한다. `KAKAO_APP_ID`는 카카오 디벨로퍼스에 표시되는 숫자 앱 ID이며 기본값은 1659627이다. 만료·무효 토큰과 다른 앱의 토큰은 401, 카카오 응답 장애는 502로 처리한다.

## 화면 전환

| 시점 | 응답 | 앱 동작 |
|---|---|---|
| 로그인 성공 | 200, data.status = PROFILE_REQUIRED | signupToken을 등록에만 사용하고 프로필 생성 화면으로 이동 |
| 로그인·프로필 완료 | 200, data.status = AUTHENTICATED | accessToken과 refreshToken을 보관하고 홈으로 이동 |
| 보호된 API 호출 | 403, code = AUTH_403_2 | 프로필 화면으로 이동. 등록 토큰이 없거나 만료됐으면 카카오 로그인부터 재개 |
| 보호된 API 호출 | 403, code = AUTH_403_1 | 정지 안내 |
| 보호된 API 호출 | 401, code = GLOBAL_401 | 유효한 자격을 확보하도록 로그인 재개. 다른 기기에서 등록을 마친 후 기존 등록 토큰을 사용한 경우도 포함 |

앱 시작·복귀·딥링크 진입 시에도 같은 규칙을 적용한다. 프로필 입력 중 앱을 닫아도 다음 카카오 로그인은 동일한 회원을 찾아 PROFILE_REQUIRED를 반환한다. 앱이 받은 로그인 응답의 data.status로 이동할 화면을 결정한다.

## 프로필 제출

앱은 `POST /api/v1/users/profile`에 `multipart/form-data`로 프로필을 제출한다. 요청 헤더에 `Authorization: Bearer <signupToken>`을 보낸다. nickname은 유니코드 문자·결합문자·숫자, ASCII 공백, 점(.), 밑줄(_), 하이픈(-), 가운뎃점(·), 아포스트로피(' 또는 ’)를 허용한다. 앞뒤 공백을 제거한 닉네임은 유니코드 코드 포인트 기준 2~10자여야 한다. phone은 입력한 원문이 ASCII 숫자 1~20자리여야 한다. profileImage는 선택이며 JPEG·PNG 파일을 받을 수 있다. 앱은 400에서 입력을 수정하고, 401에서 로그인을 재개한다. 409에서 앱은 응답 코드를 확인한다. `AUTH_409_1`이면 등록이 끝났으므로 다시 로그인한다. `AUTH_409_2`이면 프로필 입력 화면에서 다른 닉네임으로 다시 제출한다.

이전 형식의 액세스 토큰을 쓰던 앱은 401 응답 시 카카오 로그인을 한 번 다시 진행한다. 일반 재로그인은 다른 기기의 액세스 토큰 유효 기간을 유지하며, 탈퇴 후 재가입하면 이전 기기의 액세스 토큰은 무효화된다.

토큰을 포함한 성공 응답은 캐시하지 않는다. 프로필 완료 전에는 일반 로그인 토큰을 발급하지 않는다.

## 닉네임 중복 기준

닉네임은 앞뒤 ASCII 공백을 제거한 뒤 기존 MySQL 정렬 규칙인 `utf8mb4_unicode_ci`로 비교한다. 예를 들어 `Café`와 `cafe`는 같은 이름이다. 탈퇴한 회원의 닉네임도 예약되며, 같은 계정이 재가입하면서 다른 이름을 선택하면 이전 이름을 사용할 수 있다.

기존 데이터에 중복이 있으면 V5 고유 인덱스 생성이 실패한다. 배포 전에 다음 조회로 충돌하는 회원을 확인하고 닉네임을 정리한다. 회원의 기존 닉네임은 자동 변경하지 않는다.

```sql
SELECT TRIM(nickname) AS nickname, COUNT(*) AS members, GROUP_CONCAT(id ORDER BY id) AS member_ids
FROM users
WHERE OCTET_LENGTH(TRIM(nickname)) > 0
GROUP BY TRIM(nickname)
HAVING COUNT(*) > 1;
```
