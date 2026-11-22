package com.keepit.clothing.controller;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingDetailResponse;
import com.keepit.clothing.dto.ClothingIdResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.service.ClothingQueryService;
import com.keepit.clothing.service.ClothingService;
import com.keepit.global.response.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 옷 등록·조회 API (REG-320·330·340, BRW-201, BRW-210 / API 명세 13·14·18번).
 * 등록자는 액세스 토큰으로 인증된 회원이다(JWT 필터가 principal에 회원 id를 넣는다).
 * 응답의 시각은 모두 KST이고, 이미지는 URL로 준다(S3에서는 presigned 10분 유효).
 */
@Tag(name = "Clothing", description = "옷 등록·조회")
@RestController
@RequestMapping("/api/v1/clothes")
@RequiredArgsConstructor
public class ClothingController {

	private final ClothingService clothingService;
	private final ClothingQueryService clothingQueryService;

	@Operation(summary = "옷 등록 (REG-320, REG-330, REG-340)",
			description = "먼저 이미지 업로드 API로 받은 imageId를 photos에 담아 보낸다. "
					+ "일반 사진은 전면·후면·라벨이 각각 1장 이상, 3장 이상이고 하자 사진을 합쳐 10장까지. "
					+ "하자 없음이면 하자 사진·설명은 무시한다. 잡화(ACC)는 사이즈 FREE·실측 없음으로 저장한다. "
					+ "세탁 방법을 보내지 않으면 DRY_CLEAN.")
	@PostMapping
	public ResponseEntity<SuccessResponse<ClothingIdResponse>> register(
			// 로그인한 회원 id. 인증이 없거나 프로필 미완료면 보안 필터에서 먼저 401·403으로 끝난다
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody ClothingCreateRequest request) {
		// 등록자를 토큰의 회원으로 저장한다(본인이 올린 이미지만 연결된다)
		Long clothesId = clothingService.register(userId, request);
		SuccessResponse<ClothingIdResponse> body = SuccessResponse.created(new ClothingIdResponse(clothesId));
		return ResponseEntity.status(body.getHttpStatus()).body(body);
	}

	@Operation(summary = "옷 목록 (BRW-201)",
			description = "대여 가능한 옷을 최신 등록순으로 size개씩 준다(대여 중지·삭제 제외, 무한 스크롤). "
					+ "처음에는 cursor 없이 호출하고, 다음 묶음은 응답의 nextCursor를 cursor로 보낸다. size는 1~50.")
	@GetMapping
	public SuccessResponse<ClothingListResponse> getList(
			@Parameter(description = "카테고리 필터 (없으면 전체)")
			@RequestParam(required = false) ClothingCategory category,
			// 커서는 1 이상의 옷 id만 받는다. 범위 밖·형식 오류는 공통 처리에서 GLOBAL_400_3
			@Parameter(description = "앞 응답의 nextCursor (마지막으로 받은 clothesId, 없으면 처음부터)")
			@RequestParam(required = false)
			@Min(value = 1, message = "커서 값이 올바르지 않습니다.") Long cursor,
			@Parameter(description = "한 번에 받을 개수 (기본 20, 최대 50)")
			@RequestParam(defaultValue = "" + ClothingQueryService.DEFAULT_PAGE_SIZE)
			@Min(value = 1, message = "페이지 크기가 올바르지 않습니다.")
			@Max(value = ClothingQueryService.MAX_PAGE_SIZE, message = "페이지 크기가 올바르지 않습니다.") int size) {
		return SuccessResponse.ok(clothingQueryService.getList(category, cursor, size));
	}

	@Operation(summary = "옷 상세 (BRW-210)",
			description = "대여 중지 옷도 status와 함께 준다. 없거나 삭제된 옷은 CLOTHING_404_1. "
					+ "대여 불가 기간은 등록자가 막아 둔 원본 구간이고, 대여 가능 날짜 계산은 대여 일정 API를 쓴다.")
	@GetMapping("/{clothesId}")
	public SuccessResponse<ClothingDetailResponse> getDetail(@PathVariable Long clothesId) {
		return SuccessResponse.ok(clothingQueryService.getDetail(clothesId));
	}

}
