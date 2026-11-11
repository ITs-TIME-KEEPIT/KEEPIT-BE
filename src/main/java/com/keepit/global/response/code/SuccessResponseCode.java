package com.keepit.global.response.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessResponseCode implements BaseResponseCode {

	SUCCESS_OK("SUCCESS_200", HttpStatus.OK, "호출에 성공했습니다."),
	SUCCESS_CREATED("SUCCESS_201", HttpStatus.CREATED, "생성에 성공했습니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
