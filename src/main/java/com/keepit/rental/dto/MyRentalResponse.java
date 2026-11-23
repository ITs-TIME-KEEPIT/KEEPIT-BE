package com.keepit.rental.dto;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 내 거래 내역 카드 한 건 (MYP-601). 카드: 옷 사진·제목, 기간 · 지역 · 거래 방식, 상태 뱃지.
 * 카드를 누르면 거래 상세(GET /api/v1/rentals/{rentalId})로 들어간다.
 */
public record MyRentalResponse(
		@Schema(description = "대여 건 id", example = "55")
		Long rentalId,
		@Schema(description = "현재 상태", example = "ACCEPTED")
		RentalStatus status,
		@Schema(description = "옷 id", example = "10")
		Long clothingId,
		@Schema(description = "옷 제목", example = "하객룩 원피스")
		String clothingTitle,
		@Schema(description = "옷 대표 사진(전면) 주소. 응답마다 새로 발급, 사진이 없으면 null",
				example = "https://keepit-bucket.s3.ap-northeast-2.amazonaws.com/clothes/abc.jpg?X-Amz-...")
		String thumbnailUrl,
		@Schema(description = "옷 거래 지역(시군구)", example = "마포구")
		String regionName,
		@Schema(description = "받는 날", example = "2026-11-20")
		LocalDate startDate,
		@Schema(description = "돌려주는 날", example = "2026-11-22")
		LocalDate endDate,
		@Schema(description = "거래 방식. DIRECT=직거래, DELIVERY=택배", example = "DIRECT")
		TradeMethod tradeMethod,
		@Schema(description = "거래 상대 id. 빌린 옷이면 옷 주인, 빌려준 옷이면 대여자", example = "2")
		Long counterpartId,
		@Schema(description = "거래 상대 닉네임", example = "대여자")
		String counterpartNickname,
		@Schema(description = "신청 일시", example = "2026-11-18T10:00:00")
		LocalDateTime requestedAt,
		@Schema(description = "응답 기한(신청 + 48시간). 신청 상태일 때 남은 시간 표시에 쓴다", example = "2026-11-20T10:00:00")
		LocalDateTime expiresAt
) {

	public static MyRentalResponse of(Rental rental, MyRentalRole role, String thumbnailUrl) {
		User counterpart = role == MyRentalRole.BORROWER ? rental.getOwner() : rental.getBorrower();
		return new MyRentalResponse(
				rental.getId(),
				rental.getStatus(),
				rental.getClothing().getId(),
				rental.getClothing().getTitle(),
				thumbnailUrl,
				rental.getClothing().getRegion().getSigungu(),
				rental.getStartDate(),
				rental.getEndDate(),
				rental.getTradeMethod(),
				counterpart.getId(),
				counterpart.getNickname(),
				rental.getRequestedAt(),
				rental.getExpiresAt());
	}

}
