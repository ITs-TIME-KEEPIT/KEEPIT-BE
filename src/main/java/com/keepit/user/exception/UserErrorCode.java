package com.keepit.user.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * [로그인 임시] AuthErrorCode를 주석처리한 동안 프로필 등록에 쓰는 에러 코드.
 * 프론트 에러 처리가 바뀌지 않게 기존 코드 문자열(AUTH_*)을 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements BaseResponseCode {

	INVALID_NICKNAME("AUTH_400_2", HttpStatus.BAD_REQUEST, "닉네임은 허용된 문자로 2~10자를 입력해 주세요."),
	INVALID_PHONE("AUTH_400_3", HttpStatus.BAD_REQUEST, "전화번호는 0부터 9까지의 숫자 1~20자리로 입력해 주세요."),
	SUSPENDED("AUTH_403_1", HttpStatus.FORBIDDEN, "정지된 회원입니다."),
	USER_NOT_FOUND("USER_404_1", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	NICKNAME_TAKEN("AUTH_409_2", HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다. 다른 닉네임을 입력해 주세요.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
