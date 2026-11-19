package com.keepit.rental.dto;

import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalNextAction;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
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
		Long rentalId,
		RentalStatus status,
		RentalNextAction nextAction,
		ActorType nextActionBy,
		ActorType viewerRole,
		Long clothingId,
		String clothingTitle,
		Long ownerId,
		String ownerNickname,
		Long borrowerId,
		String borrowerNickname,
		LocalDate startDate,
		LocalDate endDate,
		TradeMethod tradeMethod,
		int rentalFee,
		Integer deliveryFee,
		LaundryBy laundryBy,
		LaundryMethod laundryMethod,
		RejectReason rejectReason,
		String rejectReasonDetail,
		String cancelReason,
		ActorType canceledBy,
		LocalDateTime requestedAt,
		LocalDateTime expiresAt,
		LocalDateTime acceptedAt,
		LocalDateTime handedOverAt,
		LocalDateTime receivedAt,
		LocalDateTime returnedAt,
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
