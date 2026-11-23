package com.keepit.rental.controller;

import com.keepit.global.response.SuccessResponse;
import com.keepit.rental.dto.CheckpointPhotoUploadRequest;
import com.keepit.rental.dto.MyRentalResponse;
import com.keepit.rental.dto.MyRentalRole;
import com.keepit.rental.dto.ReceivedRentalRequestResponse;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalCheckpointResponse;
import com.keepit.rental.dto.RentalCreateRequest;
import com.keepit.rental.dto.RentalDetailResponse;
import com.keepit.rental.dto.RentalIdResponse;
import com.keepit.rental.dto.RentalQuoteRequest;
import com.keepit.rental.dto.RentalQuoteResponse;
import com.keepit.rental.dto.RentalRejectRequest;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.service.MyRentalService;
import com.keepit.rental.service.RentalCheckpointService;
import com.keepit.rental.service.RentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대여 신청·수락·거절·취소, 거래 상태 전이, 체크포인트 사진, 내 거래 내역 API (RENT-410~450, TRD-501~550, MYP-601).
 *
 * <p>현재 사용자는 JWT에서 꺼낸 {@code @AuthenticationPrincipal Long userId} 로 받는다.
 */
@Tag(name = "Rental", description = "대여 신청·수락·거절·취소, 전달·수령·반납·완료, 거래 상세, 체크포인트 사진, 내 거래 내역")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RentalController {

	private final RentalService rentalService;
	private final RentalCheckpointService checkpointService;
	private final MyRentalService myRentalService;

	@Operation(summary = "대여 가능 일정 조회 (RENT-410)",
			description = "조회 구간 안에서 고를 수 없는 날짜와 사유를 직거래·택배별로 준다. 구간을 안 주면 오늘부터 60일, 최대 92일.")
	@GetMapping("/clothes/{clothesId}/availability")
	public SuccessResponse<RentalAvailabilityResponse> getAvailability(
			@PathVariable Long clothesId,
			@Parameter(description = "조회 시작일 (기본: 오늘)") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@Parameter(description = "조회 종료일 (기본: 시작일 + 59일)") @RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return SuccessResponse.ok(rentalService.getAvailability(clothesId, from, to));
	}

	@Operation(summary = "대여 신청 비용·조건 확인 (RENT-410)",
			description = "신청과 같은 검증을 거쳐 대여료·택배비·총액·세탁 조건을 계산한다. 저장하지 않는다.")
	@PostMapping("/clothes/{clothesId}/rental-quotes")
	public SuccessResponse<RentalQuoteResponse> quote(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long clothesId,
			@Valid @RequestBody RentalQuoteRequest request) {
		return SuccessResponse.ok(rentalService.quote(userId, clothesId, request.period(), request.tradeMethod()));
	}

	@Operation(summary = "대여 신청 (RENT-410)",
			description = "같은 기간 중복 신청은 허용하고, 이미 수락된 예약과 겹치면 막는다. 48시간 안에 수락되지 않으면 만료.")
	@PostMapping("/clothes/{clothesId}/rentals")
	public ResponseEntity<SuccessResponse<RentalIdResponse>> request(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long clothesId,
			@Valid @RequestBody RentalCreateRequest request) {
		Long rentalId = rentalService.request(userId, clothesId, request.period(), request.tradeMethod(),
				request.rulesAgreed());
		SuccessResponse<RentalIdResponse> body = SuccessResponse.created(new RentalIdResponse(rentalId));
		return ResponseEntity.status(body.getHttpStatus()).body(body);
	}

	@Operation(summary = "받은 대여 신청 목록 (RENT-420)", description = "내 옷에 들어온 신청. 옷·상태로 거를 수 있고 최신순.")
	@GetMapping("/me/rental-requests")
	public SuccessResponse<List<ReceivedRentalRequestResponse>> getReceivedRequests(
			@AuthenticationPrincipal Long userId,
			@Parameter(description = "특정 옷만 보기") @RequestParam(required = false) Long clothesId,
			@Parameter(description = "특정 상태만 보기 (예: REQUESTED)") @RequestParam(required = false) RentalStatus status) {
		return SuccessResponse.ok(rentalService.getReceivedRequests(userId, clothesId, status));
	}

	@Operation(summary = "내 거래 내역 (MYP-601)",
			description = "role=BORROWER는 빌린 옷, OWNER는 빌려준 옷. 모든 상태를 최신 신청순으로 준다. "
					+ "카드를 누르면 거래 상세(GET /rentals/{rentalId})로 들어간다.")
	@GetMapping("/me/rentals")
	public SuccessResponse<List<MyRentalResponse>> getMyRentals(
			@AuthenticationPrincipal Long userId,
			@Parameter(description = "탭. BORROWER=빌린 옷(기본), OWNER=빌려준 옷", example = "BORROWER")
			@RequestParam(defaultValue = "BORROWER") MyRentalRole role) {
		return SuccessResponse.ok(myRentalService.getMyRentals(userId, role));
	}

	@Operation(summary = "대여 신청 수락 (RENT-430, RENT-440)",
			description = "옷 주인만 가능. 기간이 겹치는 다른 신청은 무효(INVALIDATED) 처리된다.")
	@PatchMapping("/rentals/{rentalId}/accept")
	public SuccessResponse<RentalAcceptResult> accept(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		return SuccessResponse.ok(rentalService.accept(userId, rentalId));
	}

	@Operation(summary = "대여 신청 거절 (RENT-430)", description = "옷 주인만 가능. 사유가 기타(ETC)면 직접 입력이 필요하다.")
	@PatchMapping("/rentals/{rentalId}/reject")
	public SuccessResponse<Void> reject(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId,
			@Valid @RequestBody RentalRejectRequest request) {
		rentalService.reject(userId, rentalId, request.reason(), request.reasonDetail());
		return SuccessResponse.ok();
	}

	@Operation(summary = "거래 상세 조회 (TRD-501)",
			description = "현재 상태와 다음에 할 일, 단계별 시각을 준다. 거래 당사자만 볼 수 있다.")
	@GetMapping("/rentals/{rentalId}")
	public SuccessResponse<RentalDetailResponse> getDetail(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		return SuccessResponse.ok(rentalService.getDetail(userId, rentalId));
	}

	@Operation(summary = "전달 완료 처리 (TRD-510)", description = "옷 주인이 옷을 넘겼다고 표시한다. 수락 상태에서만 가능하고, 전달 직전 사진(HANDOVER)을 먼저 올려야 한다.")
	@PatchMapping("/rentals/{rentalId}/handover")
	public SuccessResponse<Void> handOver(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.handOver(userId, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "수령 확인 처리 (TRD-520)", description = "대여자가 옷을 받았다고 확인한다. 전달 상태에서만 가능하고, 수령 사진(RECEIVE)을 먼저 올려야 한다.")
	@PatchMapping("/rentals/{rentalId}/receipt")
	public SuccessResponse<Void> receive(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.receive(userId, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "반납 처리 (TRD-530)", description = "대여자가 옷을 돌려준다. 수령 상태에서만 가능하고, 반납 사진(RETURN)을 먼저 올려야 한다. 대여자가 세탁하는 거래는 세탁한 뒤 찍은 사진이 반납 사진이다.")
	@PatchMapping("/rentals/{rentalId}/return")
	public SuccessResponse<Void> returnItem(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.returnItem(userId, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "반납 확인·거래 완료 (TRD-540)",
			description = "옷 주인이 반납을 확인하고 거래를 끝낸다. 연락처는 완료 7일 뒤 숨겨진다.")
	@PatchMapping("/rentals/{rentalId}/complete")
	public SuccessResponse<Void> complete(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.complete(userId, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "체크포인트 사진 등록 (TRD-510, TRD-520, TRD-530)",
			description = "사진을 이미지 업로드 API로 먼저 올리고, 받은 id를 부위와 함께 보낸다. "
					+ "전면·후면·라벨 필수, 디테일 선택, 부위마다 한 장. "
					+ "HANDOVER=전달 직전(옷 주인, 수락 상태), RECEIVE=수령(대여자, 전달 상태), RETURN=반납(대여자, 수령 상태). "
					+ "같은 단계에 다시 올리면 기존 사진을 바꾼다. 사진을 올린 뒤 전달·수령·반납 API를 부른다.")
	@PostMapping("/rentals/{rentalId}/checkpoints/{stage}/photos")
	public SuccessResponse<Void> uploadCheckpointPhotos(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId,
			// RETURN_WASHED는 DB에만 있는 값이라 문서에서 뺀다(반납은 RETURN 한 번, 대여자 세탁이면 그 사진이 세탁 후 사진)
			@Parameter(description = "단계. HANDOVER=전달 직전, RECEIVE=수령, RETURN=반납", example = "HANDOVER",
					schema = @Schema(allowableValues = {"HANDOVER", "RECEIVE", "RETURN"}))
			@PathVariable RentalPhotoStage stage,
			@Valid @RequestBody CheckpointPhotoUploadRequest request) {
		checkpointService.uploadPhotos(userId, rentalId, stage, request.photos());
		return SuccessResponse.ok();
	}

	@Operation(summary = "체크포인트 사진 비교 조회 (TRD-550)",
			description = "등록 시·전달 직전·수령·반납 4단계 사진을 순서대로 준다. 거래 당사자만 볼 수 있다. "
					+ "등록 시는 옷 등록 사진이고, 아직 안 찍은 단계는 빈 목록이다.")
	@GetMapping("/rentals/{rentalId}/checkpoints")
	public SuccessResponse<RentalCheckpointResponse> getCheckpoints(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		return SuccessResponse.ok(checkpointService.getCheckpoints(userId, rentalId));
	}

	@Operation(summary = "대여 신청 취소 (RENT-450)", description = "수락 전, 신청자만 가능. 수락 후 취소는 아직 지원하지 않는다.")
	@PatchMapping("/rentals/{rentalId}/cancel")
	public SuccessResponse<Void> cancel(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.cancel(userId, rentalId);
		return SuccessResponse.ok();
	}

}
