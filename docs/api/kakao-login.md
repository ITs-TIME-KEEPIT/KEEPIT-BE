# 카카오 로그인과 프로필 등록

## 화면 전환

| 시점 | 응답 | 앱 동작 |
|---|---|---|
| 로그인 시작 | GET /api/v1/auth/kakao/login → 302 | 브라우저로 열고 같은 브라우저에서 콜백까지 진행 |
| 콜백 성공 | 200, data.status = PROFILE_REQUIRED | signupToken을 등록에만 사용하고 프로필 생성 화면으로 이동 |
| 콜백·프로필 완료 | 200, data.status = AUTHENTICATED | accessToken과 refreshToken을 보관하고 홈으로 이동 |
| 보호된 API 호출 | 403, code = AUTH_403_2 | 프로필 화면으로 이동. 등록 토큰이 없거나 만료됐으면 카카오 로그인부터 재개 |
| 보호된 API 호출 | 403, code = AUTH_403_1 | 정지 안내 |
| 보호된 API 호출 | 401, code = GLOBAL_401 | 유효한 자격을 확보하도록 로그인 재개 |

앱 시작·복귀·딥링크 진입 시에도 같은 규칙을 적용한다. 프로필 입력 중 앱을 닫아도 다음 카카오 로그인은 동일한 회원을 찾아 PROFILE_REQUIRED를 반환한다. 콜백은 브라우저에 공통 JSON 응답을 반환한다. 외부 브라우저에서 네이티브 앱으로 인증 결과를 인계하는 방식은 클라이언트와 별도로 합의해야 한다.

## 프로필 제출

POST /api/v1/auth/kakao/profile은 Authorization 헤더에 Bearer signupToken을 받고 multipart/form-data를 사용한다. nickname과 phone은 공백을 제거한 후 각각 1~20자여야 한다. profileImage는 선택이며 JPEG·PNG 파일을 받을 수 있다. 앱은 400에서 입력을 수정하고, 401에서 로그인을 재개한다. 409는 이미 프로필 등록이 끝난 경우이므로 다시 로그인하여 일반 토큰을 받는다.

토큰을 포함한 성공 응답은 캐시하지 않는다. 프로필 완료 전에는 일반 로그인 토큰을 발급하지 않는다.
