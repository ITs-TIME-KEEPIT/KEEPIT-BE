package com.keepit.rental.dto;

import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 대여 신청 요청 (POST /api/v1/clothes/{clothesId}/rentals).
 *
 * @param rulesAgreed 이용 규칙(무단 수선·변형 금지, 반납 기한 준수, 세탁 주체 확인) 동의 여부. false면 신청 불가
 */
public record RentalCreateRequest(
		@NotNull(message = "받는 날을 선택해 주세요.") LocalDate startDate,
		@NotNull(message = "돌려주는 날을 선택해 주세요.") LocalDate endDate,
		@NotNull(message = "거래 방식을 선택해 주세요.") TradeMethod tradeMethod,
		@NotNull(message = "이용 규칙 동의 여부가 필요합니다.") Boolean rulesAgreed
) {

	public RentalPeriod period() {
		return new RentalPeriod(startDate, endDate);
	}

}
