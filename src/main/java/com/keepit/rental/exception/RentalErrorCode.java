package com.keepit.rental.exception;

import com.keepit.global.response.code.BaseResponseCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 대여 도메인 에러 코드. 응답 code는 팀 규칙대로 RENTAL_{HTTP 상태}_{번호}.
 */
@Getter
@RequiredArgsConstructor
public enum RentalErrorCode implements BaseResponseCode {

	INVALID_RENTAL_PERIOD("RENTAL_400_1", HttpStatus.BAD_REQUEST, "돌려주는 날은 받는 날과 같거나 이후여야 합니다."),
	RENTAL_START_DATE_IN_PAST("RENTAL_400_2", HttpStatus.BAD_REQUEST, "오늘 이전 날짜는 선택할 수 없습니다."),
	RENTAL_PERIOD_TOO_LONG("RENTAL_400_3", HttpStatus.BAD_REQUEST, "대여 기간은 최대 7일입니다."),
	AVAILABILITY_RANGE_TOO_LONG("RENTAL_400_4", HttpStatus.BAD_REQUEST, "일정은 한 번에 최대 92일까지 조회할 수 있습니다."),
	CANNOT_RENT_OWN_CLOTHING("RENTAL_400_5", HttpStatus.BAD_REQUEST, "내가 등록한 옷은 대여할 수 없습니다."),
	DELIVERY_NOT_AVAILABLE("RENTAL_400_6", HttpStatus.BAD_REQUEST, "택배 거래를 지원하지 않는 옷입니다."),
	RULES_NOT_AGREED("RENTAL_400_7", HttpStatus.BAD_REQUEST, "이용 규칙에 동의해야 신청할 수 있습니다."),
	REJECT_REASON_REQUIRED("RENTAL_400_8", HttpStatus.BAD_REQUEST, "거절 사유를 선택해 주세요."),
	REJECT_REASON_DETAIL_REQUIRED("RENTAL_400_9", HttpStatus.BAD_REQUEST, "기타 사유를 입력해 주세요."),

	RENTAL_ACCESS_DENIED("RENTAL_403_1", HttpStatus.FORBIDDEN, "이 대여 건에 대한 권한이 없습니다."),

	RENTAL_NOT_FOUND("RENTAL_404_1", HttpStatus.NOT_FOUND, "대여 신청을 찾을 수 없습니다."),
	CLOTHING_NOT_FOUND("RENTAL_404_2", HttpStatus.NOT_FOUND, "옷을 찾을 수 없습니다."),
	USER_NOT_FOUND("RENTAL_404_3", HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),

	RENTAL_PERIOD_UNAVAILABLE("RENTAL_409_1", HttpStatus.CONFLICT, "옷 주인이 대여 불가로 지정한 기간과 겹칩니다."),
	RENTAL_PERIOD_ALREADY_RESERVED("RENTAL_409_2", HttpStatus.CONFLICT, "이미 예약된 기간과 겹칩니다."),
	CLOTHING_NOT_RENTABLE("RENTAL_409_3", HttpStatus.CONFLICT, "지금은 대여할 수 없는 옷입니다."),
	INVALID_RENTAL_STATUS("RENTAL_409_4", HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 요청입니다."),
	RENTAL_REQUEST_EXPIRED("RENTAL_409_5", HttpStatus.CONFLICT, "응답 기한(48시간)이 지난 신청입니다.");

	private final String code;
	private final HttpStatus httpStatus;
	private final String message;

}
