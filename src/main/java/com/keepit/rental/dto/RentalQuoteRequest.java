package com.keepit.rental.dto;

import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 비용·조건 확인 요청 (POST /api/v1/clothes/{clothesId}/rental-quotes).
 */
public record RentalQuoteRequest(
		@NotNull(message = "받는 날을 선택해 주세요.") LocalDate startDate,
		@NotNull(message = "돌려주는 날을 선택해 주세요.") LocalDate endDate,
		@NotNull(message = "거래 방식을 선택해 주세요.") TradeMethod tradeMethod
) {

	public RentalPeriod period() {
		return new RentalPeriod(startDate, endDate);
	}

}
