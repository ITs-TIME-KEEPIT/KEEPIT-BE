package com.keepit.rental.service;

/**
 * 달력에서 날짜를 고를 수 없는 이유 (RENT-410 "예약 불가 사유").
 */
public enum UnavailableReason {

	/** 옷 주인이 대여 불가로 막아 둔 날 */
	OWNER_BLOCKED,

	/** 이미 수락된 예약, 그 배송 기간, 예약 사이 간격에 걸리는 날 */
	RESERVED

}
