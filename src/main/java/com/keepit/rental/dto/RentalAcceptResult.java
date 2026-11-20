package com.keepit.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 수락 결과. 함께 무효 처리된 신청 id는 나중에 "무효" 알림(MYP-620)을 보낼 때 쓴다.
 */
public record RentalAcceptResult(
		@Schema(description = "수락한 대여 건 id", example = "1") Long rentalId,
		@Schema(description = "기간이 겹쳐 무효 처리된 다른 신청 id 목록", example = "[2, 3]")
		List<Long> invalidatedRentalIds) {
}
