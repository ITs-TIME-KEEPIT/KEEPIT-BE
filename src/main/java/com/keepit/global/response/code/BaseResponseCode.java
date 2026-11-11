package com.keepit.global.response.code;

import org.springframework.http.HttpStatus;

/**
 * 응답 코드 공통 인터페이스.
 * 도메인별 에러 코드도 이 인터페이스를 구현한 enum으로 각 도메인 패키지에 둔다. (예: RENTAL_409_1)
 */
public interface BaseResponseCode {

	String getCode();

	HttpStatus getHttpStatus();

	String getMessage();

}
