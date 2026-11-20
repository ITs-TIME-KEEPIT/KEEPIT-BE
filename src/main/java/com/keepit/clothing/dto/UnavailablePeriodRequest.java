package com.keepit.clothing.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 등록자가 막아 두는 대여 불가 기간. 시작일·종료일 모두 포함한다.
 */
public record UnavailablePeriodRequest(
		@NotNull(message = "대여 불가 기간의 시작일을 선택해 주세요.")
		LocalDate startDate,

		@NotNull(message = "대여 불가 기간의 종료일을 선택해 주세요.")
		LocalDate endDate
) {
}
