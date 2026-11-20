package com.keepit.rental.dto;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 받은 대여 신청 한 건 (RENT-420). 남은 응답 시간은 화면에서 expiresAt으로 계산한다.
 * 휴대폰 번호는 수락 전이라 담지 않는다.
 */
public record ReceivedRentalRequestResponse(
		@Schema(description = "대여 건 id", example = "55")
		Long rentalId,
		@Schema(description = "현재 상태", example = "REQUESTED")
		RentalStatus status,
		@Schema(description = "옷 id", example = "10")
		Long clothingId,
		@Schema(description = "옷 제목", example = "하객룩 원피스")
		String clothingTitle,
		@Schema(description = "신청자 id", example = "2")
		Long borrowerId,
		@Schema(description = "신청자 닉네임", example = "대여자")
		String borrowerNickname,
		@Schema(description = "신청자 프로필 사진 key. 없으면 null")
		String borrowerProfileImageKey,
		@Schema(description = "받는 날", example = "2026-11-20")
		LocalDate startDate,
		@Schema(description = "돌려주는 날", example = "2026-11-22")
		LocalDate endDate,
		@Schema(description = "거래 방식", example = "DIRECT")
		TradeMethod tradeMethod,
		@Schema(description = "신청 일시", example = "2026-11-18T10:00:00")
		LocalDateTime requestedAt,
		@Schema(description = "응답 기한(신청 + 48시간). 남은 시간 표시에 쓴다", example = "2026-11-20T10:00:00")
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
