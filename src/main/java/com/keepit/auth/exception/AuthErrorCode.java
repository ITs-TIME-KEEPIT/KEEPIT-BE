package com.keepit.auth.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseResponseCode {

	INVALID_CALLBACK("AUTH_400_1", HttpStatus.BAD_REQUEST, "카카오 로그인을 다시 시작해 주세요."),
	INVALID_PROFILE("AUTH_400_2", HttpStatus.BAD_REQUEST, "닉네임과 전화번호는 각각 1~20자로 입력해 주세요."),
	INVALID_SIGNUP_TOKEN("AUTH_401_2", HttpStatus.UNAUTHORIZED, "프로필 등록을 위해 카카오 로그인을 다시 해주세요."),
	PROFILE_COMPLETED("AUTH_409_1", HttpStatus.CONFLICT, "이미 등록을 완료했습니다. 다시 로그인해 주세요."),
	KAKAO_AUTH_FAILED("AUTH_401_1", HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
	PROFILE_REQUIRED("AUTH_403_2", HttpStatus.FORBIDDEN, "서비스를 이용하려면 프로필 등록을 완료해 주세요."),
	SUSPENDED("AUTH_403_1", HttpStatus.FORBIDDEN, "정지된 회원입니다."),
	KAKAO_UNAVAILABLE("AUTH_502_1", HttpStatus.BAD_GATEWAY, "카카오 서버와 통신하지 못했습니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
