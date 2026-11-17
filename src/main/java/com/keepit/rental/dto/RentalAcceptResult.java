package com.keepit.rental.dto;

import java.util.List;

/**
 * 수락 결과. 함께 무효 처리된 신청 id는 나중에 "무효" 알림(MYP-620)을 보낼 때 쓴다.
 */
public record RentalAcceptResult(Long rentalId, List<Long> invalidatedRentalIds) {
}
