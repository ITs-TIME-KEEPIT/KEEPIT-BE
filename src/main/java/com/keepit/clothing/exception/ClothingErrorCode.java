package com.keepit.clothing.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 옷 도메인 에러 코드. 응답 code는 팀 규칙대로 CLOTHING_{HTTP 상태}_{번호}.
 * 단일 필드 규칙은 Bean Validation(GLOBAL_400_2)이 맡고, 여기에는 필드 간 규칙과 DB 확인이 필요한 규칙만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum ClothingErrorCode implements BaseResponseCode {

	REQUIRED_MEASUREMENT_MISSING("CLOTHING_400_1", HttpStatus.BAD_REQUEST, "카테고리에 필요한 실측 값을 입력해 주세요."),
	SIZE_REQUIRED("CLOTHING_400_2", HttpStatus.BAD_REQUEST, "표기 사이즈를 선택해 주세요."),
	DUPLICATE_COLOR("CLOTHING_400_3", HttpStatus.BAD_REQUEST, "같은 컬러를 중복해서 선택할 수 없습니다."),
	REQUIRED_PHOTO_PART_MISSING("CLOTHING_400_4", HttpStatus.BAD_REQUEST, "전면·후면·라벨 사진이 모두 필요합니다."),
	INVALID_PHOTO_COUNT("CLOTHING_400_5", HttpStatus.BAD_REQUEST, "사진 장수가 올바르지 않습니다."),
	DEFECT_PHOTO_REQUIRED("CLOTHING_400_6", HttpStatus.BAD_REQUEST, "하자 사진을 등록해 주세요."),
	DEFECT_DESCRIPTION_REQUIRED("CLOTHING_400_7", HttpStatus.BAD_REQUEST, "하자 설명을 입력해 주세요."),
	DELIVERY_FEE_REQUIRED("CLOTHING_400_8", HttpStatus.BAD_REQUEST, "택배비를 올바르게 입력해 주세요."),
	INVALID_UNAVAILABLE_PERIOD("CLOTHING_400_9", HttpStatus.BAD_REQUEST, "대여 불가 기간이 올바르지 않습니다."),
	DUPLICATE_IMAGE("CLOTHING_400_10", HttpStatus.BAD_REQUEST, "같은 사진을 중복해서 등록할 수 없습니다."),
	UNUSABLE_IMAGE("CLOTHING_400_11", HttpStatus.BAD_REQUEST, "사용할 수 없는 사진이 포함되어 있습니다."),
	REGION_NOT_SELECTABLE("CLOTHING_400_12", HttpStatus.BAD_REQUEST, "선택할 수 없는 지역입니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
