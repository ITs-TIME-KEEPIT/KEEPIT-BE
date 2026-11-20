package com.keepit.rental.dto;

import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.TradeMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 신청 전 비용·조건 확인 (RENT-410). 신청할 때 저장될 값과 같은 계산을 쓴다.
 *
 * @param deliveryFee   보내는 택배비(편도). 직거래면 null
 * @param totalFee      대여료 + 보내는 택배비. 반납 택배비는 포함하지 않는다
 * @param returnDeliveryPaidByBorrower 택배면 true: 반납 택배비는 빌린 사람이 따로 낸다
 */
public record RentalQuoteResponse(
		@Schema(description = "옷 id", example = "10")
		Long clothingId,
		@Schema(description = "받는 날", example = "2026-11-20")
		LocalDate startDate,
		@Schema(description = "돌려주는 날", example = "2026-11-22")
		LocalDate endDate,
		@Schema(description = "대여 일수(양끝 포함)", example = "3")
		long rentalDays,
		@Schema(description = "거래 방식", example = "DIRECT")
		TradeMethod tradeMethod,
		@Schema(description = "대여료(원)", example = "15000")
		int rentalFee,
		@Schema(description = "보내는 택배비(편도, 원). 직거래면 null", example = "3500")
		Integer deliveryFee,
		@Schema(description = "대여료 + 보내는 택배비. 반납 택배비는 미포함", example = "18500")
		int totalFee,
		@Schema(description = "반납 택배비를 빌린 사람이 내야 하는지(택배 거래면 true)", example = "true")
		boolean returnDeliveryPaidByBorrower,
		@Schema(description = "세탁 주체. BORROWER=빌린 사람, OWNER=주인", example = "BORROWER")
		LaundryBy laundryBy,
		@Schema(description = "세탁 방법. DRY_CLEAN=드라이클리닝, DISCUSS=연락해서 정함", example = "DRY_CLEAN")
		LaundryMethod laundryMethod
) {

	/** 저장하지 않은 신청 객체에서 비용을 꺼낸다. */
	public static RentalQuoteResponse from(Rental draft) {
		int deliveryFee = draft.getAppliedDeliveryFee() == null ? 0 : draft.getAppliedDeliveryFee();
		boolean delivery = draft.getTradeMethod() == TradeMethod.DELIVERY;
		return new RentalQuoteResponse(
				draft.getClothing().getId(),
				draft.getStartDate(),
				draft.getEndDate(),
				draft.getPeriod().days(),
				draft.getTradeMethod(),
				draft.getAppliedRentalFee(),
				draft.getAppliedDeliveryFee(),
				draft.getAppliedRentalFee() + deliveryFee,
				delivery,
				draft.getAppliedLaundryBy(),
				draft.getAppliedLaundryMethod());
	}

}
