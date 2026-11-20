package com.keepit.rental.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 거래 상세에서 "다음에 할 일"로 보여 줄 값 (TRD-501).
 * actorType은 그 일을 해야 하는 사람이고, 끝난 거래는 NONE이라 null이다.
 */
@Getter
@RequiredArgsConstructor
public enum RentalNextAction {

	/** 옷 주인의 수락·거절을 기다린다 */
	WAIT_RESPONSE(ActorType.OWNER),
	/** 옷 주인이 옷을 넘긴다 */
	HANDOVER(ActorType.OWNER),
	/** 대여자가 수령을 확인한다 */
	RECEIVE(ActorType.BORROWER),
	/** 대여자가 반납한다 */
	RETURN(ActorType.BORROWER),
	/** 옷 주인이 반납을 확인하고 완료 처리한다 */
	COMPLETE(ActorType.OWNER),
	/** 더 할 일이 없다 (완료·거절·만료·무효·취소) */
	NONE(null);

	private final ActorType actorType;

	public static RentalNextAction of(RentalStatus status) {
		return switch (status) {
			case REQUESTED -> WAIT_RESPONSE;
			case ACCEPTED -> HANDOVER;
			case HANDED_OVER -> RECEIVE;
			case RECEIVED -> RETURN;
			case RETURNED -> COMPLETE;
			default -> NONE;
		};
	}

}
