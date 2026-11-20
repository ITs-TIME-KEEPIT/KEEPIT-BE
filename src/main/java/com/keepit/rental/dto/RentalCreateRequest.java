package com.keepit.rental.dto;

import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 대여 신청 요청 (POST /api/v1/clothes/{clothesId}/rentals).
 *
 * @param rulesAgreed 이용 규칙(무단 수선·변형 금지, 반납 기한 준수, 세탁 주체 확인) 동의 여부. false면 신청 불가
 */
public record RentalCreateRequest(
		@Schema(description = "받는 날", example = "2026-11-20", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "받는 날을 선택해 주세요.") LocalDate startDate,
		@Schema(description = "돌려주는 날. 받는 날 포함 최대 7일", example = "2026-11-22", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "돌려주는 날을 선택해 주세요.") LocalDate endDate,
		@Schema(description = "거래 방식. DIRECT=직거래, DELIVERY=택배(옷 주인이 택배 가능으로 둔 옷만)", example = "DIRECT", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "거래 방식을 선택해 주세요.") TradeMethod tradeMethod,
		@Schema(description = "이용 규칙 동의 여부. false면 신청할 수 없다", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "이용 규칙 동의 여부가 필요합니다.") Boolean rulesAgreed
) {

	public RentalPeriod period() {
		return new RentalPeriod(startDate, endDate);
	}

}
