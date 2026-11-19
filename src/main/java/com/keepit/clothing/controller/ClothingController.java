package com.keepit.clothing.controller;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingIdResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.service.ClothingQueryService;
import com.keepit.clothing.service.ClothingService;
import com.keepit.global.response.SuccessResponse;
import com.keepit.image.support.TemporaryUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옷 등록·조회 API (API 13·14·18번).
 * 로그인(JWT) 구현 전까지는 등록자 id를 TemporaryUser.ID로 고정한다.
 * 응답의 시각은 모두 KST이고, 이미지는 URL로 준다(S3에서는 presigned 10분 유효).
 */
@Tag(name = "Clothing", description = "옷 등록·조회")
@RestController
@RequestMapping("/api/v1/clothes")
@RequiredArgsConstructor
public class ClothingController {

	private final ClothingService clothingService;
	private final ClothingQueryService clothingQueryService;

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

	@Operation(summary = "옷 목록 (API 13, BRW-201)",
			description = "대여 가능한 옷을 최신 등록순으로 준다(대여 중지·삭제 제외). page는 0부터, size는 1~50.")
	@GetMapping
	public SuccessResponse<ClothingListResponse> getList(
			@Parameter(description = "카테고리 필터 (없으면 전체)")
			@RequestParam(required = false) ClothingCategory category,
			@Parameter(description = "페이지 번호 (0부터)")
			@RequestParam(defaultValue = "0")
			@Min(value = 0, message = "페이지 번호가 올바르지 않습니다.") int page,
			@Parameter(description = "페이지 크기 (기본 20, 최대 50)")
			@RequestParam(defaultValue = "" + ClothingQueryService.DEFAULT_PAGE_SIZE)
			@Min(value = 1, message = "페이지 크기가 올바르지 않습니다.")
			@Max(value = ClothingQueryService.MAX_PAGE_SIZE, message = "페이지 크기가 올바르지 않습니다.") int size) {
		return SuccessResponse.ok(clothingQueryService.getList(category, page, size));
	}

}
