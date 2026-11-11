package com.keepit.rental.dto;

import com.keepit.rental.service.UnavailableReason;
import java.time.LocalDate;
import java.util.List;

/**
 * 대여 가능 일정 (RENT-410). 조회 구간 [from, to] 안에서 고를 수 없는 날짜만 거래 방식별로 담는다.
 * 택배는 배송 기간 때문에 직거래보다 막히는 날이 많다.
 *
 * @param rentable          대여 중지·삭제된 옷이면 false (날짜와 상관없이 신청 불가)
 * @param deliveryAvailable 택배 거래를 받는 옷인지
 * @param maxRentalDays     받는 날과 돌려주는 날을 포함한 최대 대여 일수
 */
public record RentalAvailabilityResponse(
		Long clothingId,
		LocalDate from,
		LocalDate to,
		boolean rentable,
		boolean deliveryAvailable,
		int maxRentalDays,
		List<UnavailableDate> directUnavailableDates,
		List<UnavailableDate> deliveryUnavailableDates
) {

	public record UnavailableDate(LocalDate date, UnavailableReason reason) {
	}

}
