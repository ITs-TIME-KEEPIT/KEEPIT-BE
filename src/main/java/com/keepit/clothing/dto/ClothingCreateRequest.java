package com.keepit.clothing.dto;

import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 옷 등록 요청 (API 18번).
 * 단일 필드 규칙은 여기서 Bean Validation(GLOBAL_400_2)으로 검사하고,
 * 필드 간 규칙(카테고리별 실측, 사진 구성, 하자·택배·기간)은 ClothingCreateValidator가 검사한다.
 * 서버 검증은 기획 확정 전까지 디자인보다 넓게 둔다(제목 2~30자, 대여 불가 기간 선택·여러 구간).
 */
public record ClothingCreateRequest(
		@NotBlank(message = "제목을 입력해 주세요.")
		@Size(min = 2, max = 30, message = "제목 길이가 올바르지 않습니다.")
		String title,

		@Size(max = 500, message = "설명 길이가 올바르지 않습니다.")
		String description,

		@NotNull(message = "카테고리를 선택해 주세요.")
		ClothingCategory category,

		/* 잡화(ACC)는 보내지 않아도 FREE로 저장한다. */
		ClothingSize size,

		@NotNull(message = "컬러를 선택해 주세요.")
		@Size(min = 1, max = 2, message = "컬러 개수가 올바르지 않습니다.")
		List<@NotNull(message = "컬러를 선택해 주세요.") Color> colors,

		@Valid
		MeasurementRequest measurement,

		@NotNull(message = "하자 여부를 선택해 주세요.")
		Boolean hasDefect,

		@Size(max = 500, message = "하자 설명 길이가 올바르지 않습니다.")
		String defectDescription,

		@NotNull(message = "거래 지역을 선택해 주세요.")
		Integer regionId,

		@NotNull(message = "대여료를 입력해 주세요.")
		@Min(value = 1, message = "대여료가 올바르지 않습니다.")
		Integer rentalFee,

		@NotNull(message = "택배 거래 허용 여부를 선택해 주세요.")
		Boolean deliveryAvailable,

		/* 택배 가능이면 필수(0 이상), 택배 불가면 무시하고 NULL로 저장한다. */
		Integer deliveryFee,

		@NotNull(message = "세탁 주체를 선택해 주세요.")
		LaundryBy laundryBy,

		/* 보내지 않으면 DRY_CLEAN으로 저장한다. */
		LaundryMethod laundryMethod,

		@Valid
		List<@NotNull(message = "대여 불가 기간이 올바르지 않습니다.") UnavailablePeriodRequest> unavailablePeriods,

		@NotEmpty(message = "사진을 등록해 주세요.")
		@Valid
		List<@NotNull(message = "사진을 선택해 주세요.") ClothingPhotoRequest> photos
) {

	public MeasurementRequest measurementOrEmpty() {
		return measurement == null ? MeasurementRequest.EMPTY : measurement;
	}

	public List<UnavailablePeriodRequest> unavailablePeriodsOrEmpty() {
		return unavailablePeriods == null ? List.of() : unavailablePeriods;
	}

}
