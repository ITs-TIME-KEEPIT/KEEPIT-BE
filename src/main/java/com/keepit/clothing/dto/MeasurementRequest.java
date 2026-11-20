package com.keepit.clothing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/**
 * 실측 사이즈(cm). 값은 0.1 이상, DECIMAL(5,1) 범위.
 * 카테고리별 필수 항목은 ClothingCreateValidator가 확인하고, 카테고리에 없는 항목은 저장할 때 NULL로 바뀐다.
 */
public record MeasurementRequest(
		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal totalLength,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal shoulderWidth,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal chestWidth,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal sleeveLength,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal waistWidth,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal hipWidth,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal thighWidth,

		@DecimalMin(value = "0.1", message = "실측 값이 올바르지 않습니다.")
		@Digits(integer = 4, fraction = 1, message = "실측 값이 올바르지 않습니다.")
		BigDecimal hemWidth
) {

	public static final MeasurementRequest EMPTY = new MeasurementRequest(null, null, null, null, null, null, null, null);

}
