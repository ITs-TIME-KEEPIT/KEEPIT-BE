package com.keepit.rental.dto;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 받은 대여 신청 한 건 (RENT-420). 남은 응답 시간은 화면에서 expiresAt으로 계산한다.
 * 휴대폰 번호는 수락 전이라 담지 않는다.
 */
public record ReceivedRentalRequestResponse(
		Long rentalId,
		RentalStatus status,
		Long clothingId,
		String clothingTitle,
		Long borrowerId,
		String borrowerNickname,
		String borrowerProfileImageKey,
		LocalDate startDate,
		LocalDate endDate,
		TradeMethod tradeMethod,
		LocalDateTime requestedAt,
		LocalDateTime expiresAt
) {

	public static ReceivedRentalRequestResponse from(Rental rental) {
		return new ReceivedRentalRequestResponse(
				rental.getId(),
				rental.getStatus(),
				rental.getClothing().getId(),
				rental.getClothing().getTitle(),
				rental.getBorrower().getId(),
				rental.getBorrower().getNickname(),
				rental.getBorrower().getProfileImageKey(),
				rental.getStartDate(),
				rental.getEndDate(),
				rental.getTradeMethod(),
				rental.getRequestedAt(),
				rental.getExpiresAt());
	}

}
