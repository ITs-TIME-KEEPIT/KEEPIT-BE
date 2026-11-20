package com.keepit.clothing.controller;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingIdResponse;
import com.keepit.clothing.service.ClothingService;
import com.keepit.global.response.SuccessResponse;
import com.keepit.image.support.TemporaryUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옷 등록 API (API 18번, REG-320·330·340).
 * 로그인(JWT) 구현 전까지는 등록자 id를 TemporaryUser.ID로 고정한다.
 */
@Tag(name = "Clothing", description = "옷 등록·조회")
@RestController
@RequestMapping("/api/v1/clothes")
@RequiredArgsConstructor
public class ClothingController {

	private final ClothingService clothingService;

	@Operation(summary = "옷 등록 (API 18)",
			description = "먼저 이미지 업로드 API로 받은 imageId를 photos에 담아 보낸다. "
					+ "일반 사진은 전면·후면·라벨이 각각 1장 이상, 3장 이상이고 하자 사진을 합쳐 10장까지. "
					+ "하자 없음이면 하자 사진·설명은 무시한다. 잡화(ACC)는 사이즈 FREE·실측 없음으로 저장한다. "
					+ "세탁 방법을 보내지 않으면 DRY_CLEAN.")
	@PostMapping
	public ResponseEntity<SuccessResponse<ClothingIdResponse>> register(
			@Valid @RequestBody ClothingCreateRequest request) {
		Long clothesId = clothingService.register(TemporaryUser.ID, request);
		SuccessResponse<ClothingIdResponse> body = SuccessResponse.created(new ClothingIdResponse(clothesId));
		return ResponseEntity.status(body.getHttpStatus()).body(body);
	}

}
