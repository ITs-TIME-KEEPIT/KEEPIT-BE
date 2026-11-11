package com.keepit.global.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;

/**
 * 비즈니스 예외의 기반 클래스.
 * 도메인 예외는 이 클래스를 상속하고, 도메인 에러 코드(BaseResponseCode 구현 enum)를 넘긴다.
 */
@Getter
public class BaseException extends RuntimeException {

	private final BaseResponseCode responseCode;

	public BaseException(BaseResponseCode responseCode) {
		super(responseCode.getMessage());
		this.responseCode = responseCode;
	}

}
