package com.keepit.rental.exception;

import com.keepit.global.exception.BaseException;

/**
 * 대여 규칙 위반 예외. 팀 GlobalExceptionHandler가 BaseException으로 받아 ErrorResponse로 응답한다.
 */
public class RentalException extends BaseException {

	public RentalException(RentalErrorCode errorCode) {
		super(errorCode);
	}

}
