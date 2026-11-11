package com.keepit.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인과 무관한 공통 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorResponseCode implements BaseResponseCode {

	BAD_REQUEST_ERROR("GLOBAL_400_1", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
	INVALID_HTTP_MESSAGE_BODY("GLOBAL_400_2", HttpStatus.BAD_REQUEST, "HTTP 요청 바디 형식이 올바르지 않습니다."),
	INVALID_HTTP_MESSAGE_PARAMETER("GLOBAL_400_3", HttpStatus.BAD_REQUEST, "HTTP 요청 파라미터가 올바르지 않습니다."),
	UNAUTHORIZED_REQUEST("GLOBAL_401", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	ACCESS_DENIED_REQUEST("GLOBAL_403", HttpStatus.FORBIDDEN, "해당 요청에 대한 접근 권한이 없습니다."),
	NOT_FOUND_ENDPOINT("GLOBAL_404", HttpStatus.NOT_FOUND, "존재하지 않는 엔드포인트입니다."),
	UNSUPPORTED_HTTP_METHOD("GLOBAL_405", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메소드입니다."),
	UNSUPPORTED_MEDIA_TYPE("GLOBAL_415", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
	SERVER_ERROR("GLOBAL_500", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
