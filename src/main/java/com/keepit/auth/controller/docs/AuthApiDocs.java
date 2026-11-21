package com.keepit.auth.controller.docs;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.dto.KakaoLoginRequest;
import com.keepit.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.ResponseEntity;

/** 카카오 로그인 API의 Swagger 설명을 컨트롤러 구현과 분리한다. */
public final class AuthApiDocs {

	private AuthApiDocs() {
	}

	@Tag(name = "Auth", description = "카카오 모바일 로그인")
	public interface KakaoLogin {

		@SecurityRequirements
		@Operation(summary = "카카오 모바일 로그인", description = """
				앱은 카카오 SDK에서 받은 **카카오 액세스 토큰**을 요청 본문의 `accessToken`에 넣어 로그인한다. 로그인 결과는 프로필 등록 여부에 따라 나뉜다.

				- `PROFILE_REQUIRED` (신규·프로필 미완료 회원): 임시 `signupToken`만 발급된다. **다음 필수 호출은 `POST /api/v1/users/profile`**이다. `Authorization: Bearer <signupToken>`으로 프로필을 제출한다. 등록 성공 응답에서 KEEPIT `accessToken`·`refreshToken`을 받아 저장하고 홈으로 이동한다.
				- `AUTHENTICATED` (프로필 완료 회원): 로그인 응답에서 KEEPIT `accessToken`·`refreshToken`이 바로 발급된다. **다음 필수 API 호출은 없다.** 앱은 두 토큰을 저장하고 홈으로 이동한다.

				프로필 입력 중 앱을 종료하면 다음 카카오 로그인에서도 `PROFILE_REQUIRED`가 반환된다. `signupToken`은 프로필 등록 API에만 사용한다.
				""")
		@ApiResponses({
				@ApiResponse(responseCode = "200", description = "카카오 인증 성공"),
				@ApiResponse(responseCode = "400", description = "카카오 액세스 토큰 입력 오류"),
				@ApiResponse(responseCode = "401", description = "유효하지 않거나 다른 앱에 발급된 토큰"),
				@ApiResponse(responseCode = "403", description = "정지 회원"),
				@ApiResponse(responseCode = "502", description = "카카오 서버 통신 실패")
		})
		ResponseEntity<SuccessResponse<LoginResult>> login(KakaoLoginRequest request);
	}

}
