package com.keepit.global.response;

import com.keepit.global.response.code.BaseResponseCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Getter;

/**
 * 모든 API 응답의 공통 필드.
 */
@Getter
public abstract class BaseResponse {

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final Boolean success;
	private final String timestamp;
	private final String code;
	private final int httpStatus;
	private final String message;

	protected BaseResponse(Boolean success, BaseResponseCode responseCode, String message) {
		this.success = success;
		this.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		this.code = responseCode.getCode();
		this.httpStatus = responseCode.getHttpStatus().value();
		this.message = message;
	}

}
