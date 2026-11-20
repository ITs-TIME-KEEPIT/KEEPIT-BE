package com.keepit.rental.dto;

import com.keepit.rental.service.UnavailableReason;
import io.swagger.v3.oas.annotations.media.Schema;
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
		@Schema(description = "옷 id", example = "10")
		Long clothingId,
		@Schema(description = "조회 시작일", example = "2026-11-20")
		LocalDate from,
		@Schema(description = "조회 종료일", example = "2027-01-18")
		LocalDate to,
		@Schema(description = "지금 대여 신청을 받는 옷인지. 대여 중지·삭제면 false", example = "true")
		boolean rentable,
		@Schema(description = "택배 거래가 가능한 옷인지", example = "true")
		boolean deliveryAvailable,
		@Schema(description = "최대 대여 일수(받는 날·돌려주는 날 포함)", example = "7")
		int maxRentalDays,
		@Schema(description = "직거래로 고를 수 없는 날짜와 사유")
		List<UnavailableDate> directUnavailableDates,
		@Schema(description = "택배로 고를 수 없는 날짜와 사유. 배송 기간 때문에 직거래보다 넓다")
		List<UnavailableDate> deliveryUnavailableDates
) {

	public record UnavailableDate(LocalDate date, UnavailableReason reason) {
	}

}
