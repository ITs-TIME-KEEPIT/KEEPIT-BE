package com.keepit.rental.dto;

import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 비용·조건 확인 요청 (POST /api/v1/clothes/{clothesId}/rental-quotes).
 */
public record RentalQuoteRequest(
		@Schema(description = "받는 날", example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "받는 날을 선택해 주세요.") LocalDate startDate,
		@Schema(description = "돌려주는 날. 받는 날 포함 최대 7일", example = "2026-11-22", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "돌려주는 날을 선택해 주세요.") LocalDate endDate,
		@Schema(description = "거래 방식. DIRECT=직거래, DELIVERY=택배", example = "DIRECT", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "거래 방식을 선택해 주세요.") TradeMethod tradeMethod
) {

	public RentalPeriod period() {
		return new RentalPeriod(startDate, endDate);
	}

}
