package com.keepit.image.exception;

import com.keepit.global.exception.BaseException;

/**
 * 이미지 업로드 규칙 위반·저장 실패 예외. 팀 GlobalExceptionHandler가 BaseException으로 받아 ErrorResponse로 응답한다.
 */
public class ImageException extends BaseException {

	public ImageException(ImageErrorCode errorCode) {
		super(errorCode);
	}

	public ImageException(ImageErrorCode errorCode, Throwable cause) {
		super(errorCode);
		initCause(cause);
	}

}
