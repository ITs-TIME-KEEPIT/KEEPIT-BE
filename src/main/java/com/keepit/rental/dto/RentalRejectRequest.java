package com.keepit.rental.dto;

import com.keepit.rental.entity.RejectReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 대여 신청 거절 요청 (PATCH /api/v1/rentals/{rentalId}/reject).
 *
 * @param reasonDetail 사유가 ETC(기타)일 때만 필수
 */
public record RentalRejectRequest(
		@Schema(description = "거절 사유. OWNER_USE=직접 입을 예정, BAD_CONDITION=옷 상태, TOO_FAR=거리, ETC=기타",
				example = "OWNER_USE", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "거절 사유를 선택해 주세요.") RejectReason reason,
		@Schema(description = "기타 사유 직접 입력. reason이 ETC일 때만 필수, 255자까지", example = "일정이 바뀌었어요")
		@Size(max = 255, message = "기타 사유는 255자까지 입력할 수 있습니다.") String reasonDetail
) {
}
