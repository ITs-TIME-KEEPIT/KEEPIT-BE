package com.keepit.rental.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 대여 도메인 에러 코드.
 * TODO 팀 공통 응답 코드(BaseResponseCode)가 develop에 들어오면 그 인터페이스를 구현하도록 바꾼다.
 */
@Getter
@RequiredArgsConstructor
public enum RentalErrorCode {

	RENTAL_NOT_FOUND(HttpStatus.NOT_FOUND, "대여 신청을 찾을 수 없습니다."),
	RENTAL_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 대여 건에 대한 권한이 없습니다."),

	INVALID_RENTAL_PERIOD(HttpStatus.BAD_REQUEST, "돌려주는 날은 받는 날과 같거나 이후여야 합니다."),
	RENTAL_START_DATE_IN_PAST(HttpStatus.BAD_REQUEST, "오늘 이전 날짜는 선택할 수 없습니다."),
	RENTAL_PERIOD_TOO_LONG(HttpStatus.BAD_REQUEST, "대여 기간은 최대 7일입니다."),
	RENTAL_PERIOD_UNAVAILABLE(HttpStatus.CONFLICT, "옷 주인이 대여 불가로 지정한 기간과 겹칩니다."),
	RENTAL_PERIOD_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약된 기간과 겹칩니다."),

	CLOTHING_NOT_RENTABLE(HttpStatus.CONFLICT, "지금은 대여할 수 없는 옷입니다."),
	CANNOT_RENT_OWN_CLOTHING(HttpStatus.BAD_REQUEST, "내가 등록한 옷은 대여할 수 없습니다."),
	DELIVERY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "택배 거래를 지원하지 않는 옷입니다."),
	RULES_NOT_AGREED(HttpStatus.BAD_REQUEST, "이용 규칙에 동의해야 신청할 수 있습니다."),

	INVALID_RENTAL_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 할 수 없는 요청입니다."),
	RENTAL_REQUEST_EXPIRED(HttpStatus.CONFLICT, "응답 기한(48시간)이 지난 신청입니다."),
	REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "거절 사유를 선택해 주세요."),
	REJECT_REASON_DETAIL_REQUIRED(HttpStatus.BAD_REQUEST, "기타 사유를 입력해 주세요.");

	private final HttpStatus status;
	private final String message;

}
