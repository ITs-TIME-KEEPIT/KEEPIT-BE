package com.keepit.clothing.exception;

import com.keepit.global.exception.BaseException;

/**
 * 옷 등록·조회 규칙 위반 예외. 팀 GlobalExceptionHandler가 BaseException으로 받아 ErrorResponse로 응답한다.
 */
public class ClothingException extends BaseException {

	public ClothingException(ClothingErrorCode errorCode) {
		super(errorCode);
	}

}
