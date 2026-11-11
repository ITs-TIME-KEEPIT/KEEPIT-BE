package com.keepit.global.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.keepit.global.response.code.SuccessResponseCode;
import lombok.Getter;

@Getter
@JsonPropertyOrder({"success", "timestamp", "code", "httpStatus", "message", "data"})
public class SuccessResponse<T> extends BaseResponse {

	private final T data;

	private SuccessResponse(T data, SuccessResponseCode responseCode) {
		super(true, responseCode, responseCode.getMessage());
		this.data = data;
	}

	public static <T> SuccessResponse<T> ok(T data) {
		return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_OK);
	}

	public static SuccessResponse<Void> ok() {
		return new SuccessResponse<>(null, SuccessResponseCode.SUCCESS_OK);
	}

	public static <T> SuccessResponse<T> created(T data) {
		return new SuccessResponse<>(data, SuccessResponseCode.SUCCESS_CREATED);
	}

}
