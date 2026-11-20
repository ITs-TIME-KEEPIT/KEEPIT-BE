package com.keepit.clothing.dto;

import com.keepit.clothing.entity.PhotoPart;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 옷에 연결할 업로드 이미지. sortOrder는 받은 값 그대로 저장한다(중복 허용, 조회 시 sort_order, id 순).
 */
public record ClothingPhotoRequest(
		@NotNull(message = "사진을 선택해 주세요.")
		Long imageId,

		@NotNull(message = "사진 부위를 선택해 주세요.")
		PhotoPart photoPart,

		@NotNull(message = "사진 순서가 필요합니다.")
		@Min(value = 0, message = "사진 순서가 올바르지 않습니다.")
		Integer sortOrder
) {
}
