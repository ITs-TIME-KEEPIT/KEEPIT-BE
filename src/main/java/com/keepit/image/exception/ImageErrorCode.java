package com.keepit.image.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 이미지 도메인 에러 코드. 응답 code는 팀 규칙대로 IMAGE_{HTTP 상태}_{번호}.
 */
@Getter
@RequiredArgsConstructor
public enum ImageErrorCode implements BaseResponseCode {

	IMAGE_FILE_REQUIRED("IMAGE_400_1", HttpStatus.BAD_REQUEST, "업로드할 이미지 파일이 없습니다."),
	UNSUPPORTED_IMAGE_FORMAT("IMAGE_400_2", HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),

	IMAGE_UPLOAD_FAILED("IMAGE_500_1", HttpStatus.INTERNAL_SERVER_ERROR, "이미지를 저장하지 못했습니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
