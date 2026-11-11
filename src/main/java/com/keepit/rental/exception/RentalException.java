package com.keepit.rental.exception;

import lombok.Getter;

/**
 * 대여 규칙 위반 예외.
 * TODO 팀 공통 예외가 develop에 들어오면 그 예외를 상속하도록 바꾼다.
 */
@Getter
public class RentalException extends RuntimeException {

	private final RentalErrorCode errorCode;

	public RentalException(RentalErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

}
