package com.keepit.auth.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseResponseCode {

	INVALID_CALLBACK("AUTH_400_1", HttpStatus.BAD_REQUEST, "카카오 로그인을 다시 시작해 주세요."),
	KAKAO_AUTH_FAILED("AUTH_401_1", HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다."),
	SUSPENDED("AUTH_403_1", HttpStatus.FORBIDDEN, "정지된 회원입니다."),
	KAKAO_UNAVAILABLE("AUTH_502_1", HttpStatus.BAD_GATEWAY, "카카오 서버와 통신하지 못했습니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
