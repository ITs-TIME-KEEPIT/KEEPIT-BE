package com.keepit.rental.dto;

import com.keepit.clothing.entity.PhotoPart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 체크포인트 사진 등록 요청 (POST /api/v1/rentals/{rentalId}/checkpoints/{stage}/photos).
 * 사진은 먼저 이미지 업로드 API(POST /api/v1/images)로 올리고, 받은 imageId를 부위와 함께 보낸다.
 * 목록 순서가 사진 순서가 된다.
 */
public record CheckpointPhotoUploadRequest(
		@Schema(description = "올릴 사진 목록. 전면·후면·라벨 필수, 디테일 선택, 부위마다 한 장(최대 4장)",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotEmpty(message = "사진을 올려 주세요.")
		@Size(max = 4, message = "사진은 최대 4장까지 올릴 수 있습니다.")
		List<@Valid @NotNull(message = "사진 정보가 비어 있습니다.") Photo> photos
) {

	public record Photo(
			@Schema(description = "이미지 업로드 API로 받은 이미지 id", example = "55",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "사진을 선택해 주세요.") Long imageId,
			@Schema(description = "촬영 부위. FRONT=전면, BACK=후면, LABEL=라벨, DETAIL=디테일", example = "FRONT",
					requiredMode = Schema.RequiredMode.REQUIRED)
			@NotNull(message = "사진 부위를 선택해 주세요.") PhotoPart photoPart
	) {
	}

}
