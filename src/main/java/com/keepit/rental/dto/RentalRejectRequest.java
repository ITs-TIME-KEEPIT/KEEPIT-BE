package com.keepit.rental.dto;

import com.keepit.rental.entity.RejectReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 대여 신청 거절 요청 (PATCH /api/v1/rentals/{rentalId}/reject).
 *
 * @param reasonDetail 사유가 ETC(기타)일 때만 필수
 */
public record RentalRejectRequest(
		@NotNull(message = "거절 사유를 선택해 주세요.") RejectReason reason,
		@Size(max = 255, message = "기타 사유는 255자까지 입력할 수 있습니다.") String reasonDetail
) {
}
