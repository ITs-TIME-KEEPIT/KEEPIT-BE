package com.keepit.rental.dto;

import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.TradeMethod;
import java.time.LocalDate;

/**
 * 신청 전 비용·조건 확인 (RENT-410). 신청할 때 저장될 값과 같은 계산을 쓴다.
 *
 * @param deliveryFee   보내는 택배비(편도). 직거래면 null
 * @param totalFee      대여료 + 보내는 택배비. 반납 택배비는 포함하지 않는다
 * @param returnDeliveryPaidByBorrower 택배면 true: 반납 택배비는 빌린 사람이 따로 낸다
 */
public record RentalQuoteResponse(
		Long clothingId,
		LocalDate startDate,
		LocalDate endDate,
		long rentalDays,
		TradeMethod tradeMethod,
		int rentalFee,
		Integer deliveryFee,
		int totalFee,
		boolean returnDeliveryPaidByBorrower,
		LaundryBy laundryBy,
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
