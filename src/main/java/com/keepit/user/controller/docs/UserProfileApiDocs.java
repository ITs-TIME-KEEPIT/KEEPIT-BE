package com.keepit.user.controller.docs;

import com.keepit.auth.dto.LoginResult;
import com.keepit.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Users", description = "회원 프로필")
public interface UserProfileApiDocs {

	@Operation(summary = "회원 프로필 등록", description = """
			먼저 로그인 API를 호출해 `data.status=PROFILE_REQUIRED`와 `data.signupToken`을 받아야 한다. 현재 카카오 로그인은 `POST /api/v1/auth/kakao/login`이다.

			받은 `signupToken`을 `Authorization: Bearer <signupToken>`으로 보내 닉네임·전화번호와 선택 사항인 사진을 등록한다. 성공 응답에는 `data.status=AUTHENTICATED`와 KEEPIT `accessToken`·`refreshToken`이 들어 있다. 이후 필수 API 호출은 없으며 앱은 토큰을 저장하고 홈으로 이동한다.
			""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "프로필 등록 및 로그인 완료"),
			@ApiResponse(responseCode = "400", description = "닉네임 또는 전화번호 입력 오류"),
			@ApiResponse(responseCode = "401", description = "프로필 등록 토큰 누락·만료·무효"),
			@ApiResponse(responseCode = "403", description = "정지 회원"),
			@ApiResponse(responseCode = "409", description = "AUTH_409_1은 등록 완료, AUTH_409_2는 다른 회원이 사용 중인 닉네임")
	})
	ResponseEntity<SuccessResponse<LoginResult>> register(
			@Parameter(name = "Authorization", in = ParameterIn.HEADER, description = "Bearer 프로필 등록 토큰") String authorization,
			@Parameter(description = "닉네임 (필수). 유니코드 문자·결합문자·숫자, 공백, 점·밑줄·하이픈·가운뎃점·아포스트로피 허용. 앞뒤 공백 제거 후 유니코드 코드 포인트 기준 2~10자. 다른 회원과 중복 불가") String nickname,
			@Parameter(description = "전화번호 (필수). 공백·하이픈 없이 ASCII 숫자 1~20자리") String phone,
			@Parameter(description = "프로필 사진 (선택). JPEG·PNG, 파일명 확장자가 실제 형식과 같아야 함") MultipartFile profileImage);
}
