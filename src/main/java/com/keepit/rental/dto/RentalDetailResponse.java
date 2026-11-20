package com.keepit.rental.dto;

import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalNextAction;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 거래 상세 (TRD-501). 현재 상태와 다음에 할 일, 단계별 시각을 함께 준다.
 * 휴대폰 번호 공개(수락~완료 7일)는 프로필 데이터가 준비된 뒤 추가한다.
 *
 * @param nextAction   다음에 해야 할 일. 끝난 거래는 NONE
 * @param nextActionBy 그 일을 할 사람. NONE이면 null
 * @param viewerRole   조회한 사람이 이 거래에서 맡은 역할 (OWNER 또는 BORROWER)
 */
public record RentalDetailResponse(
		@Schema(description = "대여 건 id", example = "1")
		Long rentalId,
		@Schema(description = "현재 상태", example = "ACCEPTED")
		RentalStatus status,
		@Schema(description = "다음에 할 일. WAIT_RESPONSE/HANDOVER/RECEIVE/RETURN/COMPLETE/NONE", example = "HANDOVER")
		RentalNextAction nextAction,
		@Schema(description = "그 일을 할 사람. NONE이면 null", example = "OWNER")
		ActorType nextActionBy,
		@Schema(description = "조회한 사람의 역할", example = "OWNER")
		ActorType viewerRole,
		@Schema(description = "옷 id", example = "10")
		Long clothingId,
		@Schema(description = "옷 제목", example = "하객룩 원피스")
		String clothingTitle,
		@Schema(description = "옷 주인 id", example = "1")
		Long ownerId,
		@Schema(description = "옷 주인 닉네임", example = "주인")
		String ownerNickname,
		@Schema(description = "대여자 id", example = "2")
		Long borrowerId,
		@Schema(description = "대여자 닉네임", example = "대여자")
		String borrowerNickname,
		@Schema(description = "받는 날", example = "2026-11-20")
		LocalDate startDate,
		@Schema(description = "돌려주는 날", example = "2026-11-22")
		LocalDate endDate,
		@Schema(description = "거래 방식", example = "DIRECT")
		TradeMethod tradeMethod,
		@Schema(description = "신청 당시 대여료(원)", example = "15000")
		int rentalFee,
		@Schema(description = "신청 당시 보내는 택배비. 직거래면 null")
		Integer deliveryFee,
		@Schema(description = "신청 당시 세탁 주체", example = "BORROWER")
		LaundryBy laundryBy,
		@Schema(description = "신청 당시 세탁 방법", example = "DRY_CLEAN")
		LaundryMethod laundryMethod,
		@Schema(description = "거절 사유. 거절된 건만")
		RejectReason rejectReason,
		@Schema(description = "거절 사유 직접 입력(ETC)")
		String rejectReasonDetail,
		@Schema(description = "취소 사유")
		String cancelReason,
		@Schema(description = "취소한 주체")
		ActorType canceledBy,
		@Schema(description = "신청 일시", example = "2026-11-18T10:00:00")
		LocalDateTime requestedAt,
		@Schema(description = "응답 기한(신청 + 48시간)", example = "2026-11-20T10:00:00")
		LocalDateTime expiresAt,
		@Schema(description = "수락 일시", example = "2026-11-18T12:00:00")
		LocalDateTime acceptedAt,
		@Schema(description = "전달 일시")
		LocalDateTime handedOverAt,
		@Schema(description = "수령 일시")
		LocalDateTime receivedAt,
		@Schema(description = "반납 일시")
		LocalDateTime returnedAt,
		@Schema(description = "거래 완료 일시")
		LocalDateTime completedAt
) {

	public static RentalDetailResponse of(Rental rental, ActorType viewerRole) {
		RentalNextAction nextAction = rental.nextAction();
		return new RentalDetailResponse(
				rental.getId(),
				rental.getStatus(),
				nextAction,
				nextAction.getActorType(),
				viewerRole,
				rental.getClothing().getId(),
				rental.getClothing().getTitle(),
				rental.getOwner().getId(),
				rental.getOwner().getNickname(),
				rental.getBorrower().getId(),
				rental.getBorrower().getNickname(),
				rental.getStartDate(),
				rental.getEndDate(),
				rental.getTradeMethod(),
				rental.getAppliedRentalFee(),
				rental.getAppliedDeliveryFee(),
				rental.getAppliedLaundryBy(),
				rental.getAppliedLaundryMethod(),
				rental.getRejectReason(),
				rental.getRejectReasonDetail(),
				rental.getCancelReason(),
				rental.getCanceledBy(),
				rental.getRequestedAt(),
				rental.getExpiresAt(),
				rental.getAcceptedAt(),
				rental.getHandedOverAt(),
				rental.getReceivedAt(),
				rental.getReturnedAt(),
				rental.getCompletedAt());
	}

}
