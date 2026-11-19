package com.keepit.rental.controller;

import com.keepit.global.response.SuccessResponse;
import com.keepit.rental.dto.ReceivedRentalRequestResponse;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalCreateRequest;
import com.keepit.rental.dto.RentalDetailResponse;
import com.keepit.rental.dto.RentalIdResponse;
import com.keepit.rental.dto.RentalQuoteRequest;
import com.keepit.rental.dto.RentalQuoteResponse;
import com.keepit.rental.dto.RentalRejectRequest;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.service.RentalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대여 신청·수락·거절·취소 API (RENT-410~450).
 *
 * <p>로그인(JWT) 구현 전까지는 11/10 회의 결정대로 사용자 id를 고정한다.
 * 대여는 신청자와 옷 주인이 서로 달라야 해서 두 개를 쓴다.
 * 로그인이 완료되면 두 상수를 {@code @AuthenticationPrincipal Long userId} 로 바꾼다.
 */
@Tag(name = "Rental", description = "대여 신청·수락·거절·취소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RentalController {

	/** [로그인 전 임시] 옷 주인. 수락·거절에 쓴다. */
	private static final Long OWNER_ID = 1L;

	/** [로그인 전 임시] 대여자. 신청·비용 확인·취소에 쓴다. */
	private static final Long BORROWER_ID = 2L;

	private final RentalService rentalService;

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
			@PathVariable Long clothesId,
			@Valid @RequestBody RentalQuoteRequest request) {
		return SuccessResponse.ok(rentalService.quote(BORROWER_ID, clothesId, request.period(), request.tradeMethod()));
	}

	@Operation(summary = "대여 신청 (RENT-410)",
			description = "같은 기간 중복 신청은 허용하고, 이미 수락된 예약과 겹치면 막는다. 48시간 안에 수락되지 않으면 만료.")
	@PostMapping("/clothes/{clothesId}/rentals")
	public ResponseEntity<SuccessResponse<RentalIdResponse>> request(
			@PathVariable Long clothesId,
			@Valid @RequestBody RentalCreateRequest request) {
		Long rentalId = rentalService.request(BORROWER_ID, clothesId, request.period(), request.tradeMethod(),
				request.rulesAgreed());
		SuccessResponse<RentalIdResponse> body = SuccessResponse.created(new RentalIdResponse(rentalId));
		return ResponseEntity.status(body.getHttpStatus()).body(body);
	}

	@Operation(summary = "받은 대여 신청 목록 (RENT-420)", description = "내 옷에 들어온 신청. 옷·상태로 거를 수 있고 최신순.")
	@GetMapping("/me/rental-requests")
	public SuccessResponse<List<ReceivedRentalRequestResponse>> getReceivedRequests(
			@Parameter(description = "특정 옷만 보기") @RequestParam(required = false) Long clothesId,
			@Parameter(description = "특정 상태만 보기 (예: REQUESTED)") @RequestParam(required = false) RentalStatus status) {
		return SuccessResponse.ok(rentalService.getReceivedRequests(OWNER_ID, clothesId, status));
	}

	@Operation(summary = "대여 신청 수락 (RENT-430, RENT-440)",
			description = "옷 주인만 가능. 기간이 겹치는 다른 신청은 무효(INVALIDATED) 처리된다.")
	@PatchMapping("/rentals/{rentalId}/accept")
	public SuccessResponse<RentalAcceptResult> accept(
			@PathVariable Long rentalId) {
		return SuccessResponse.ok(rentalService.accept(OWNER_ID, rentalId));
	}

	@Operation(summary = "대여 신청 거절 (RENT-430)", description = "옷 주인만 가능. 사유가 기타(ETC)면 직접 입력이 필요하다.")
	@PatchMapping("/rentals/{rentalId}/reject")
	public SuccessResponse<Void> reject(
			@PathVariable Long rentalId,
			@Valid @RequestBody RentalRejectRequest request) {
		rentalService.reject(OWNER_ID, rentalId, request.reason(), request.reasonDetail());
		return SuccessResponse.ok();
	}

	@Operation(summary = "거래 상세 조회 (TRD-501)",
			description = "현재 상태와 다음에 할 일, 단계별 시각을 준다. 거래 당사자만 볼 수 있다.")
	@GetMapping("/rentals/{rentalId}")
	public SuccessResponse<RentalDetailResponse> getDetail(
			@PathVariable Long rentalId,
			@Parameter(description = "[로그인 전 임시] 조회자 id. 1=옷 주인, 2=대여자")
			@RequestParam(defaultValue = "2") Long viewerId) {
		return SuccessResponse.ok(rentalService.getDetail(viewerId, rentalId));
	}

	@Operation(summary = "전달 완료 처리 (TRD-510)", description = "옷 주인이 옷을 넘겼다고 표시한다. 수락 상태에서만 가능.")
	@PatchMapping("/rentals/{rentalId}/handover")
	public SuccessResponse<Void> handOver(@PathVariable Long rentalId) {
		rentalService.handOver(OWNER_ID, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "수령 확인 처리 (TRD-520)", description = "대여자가 옷을 받았다고 확인한다. 전달 상태에서만 가능.")
	@PatchMapping("/rentals/{rentalId}/receipt")
	public SuccessResponse<Void> receive(@PathVariable Long rentalId) {
		rentalService.receive(BORROWER_ID, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "반납 처리 (TRD-530)", description = "대여자가 옷을 돌려준다. 수령 상태에서만 가능.")
	@PatchMapping("/rentals/{rentalId}/return")
	public SuccessResponse<Void> returnItem(@PathVariable Long rentalId) {
		rentalService.returnItem(BORROWER_ID, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "반납 확인·거래 완료 (TRD-540)",
			description = "옷 주인이 반납을 확인하고 거래를 끝낸다. 연락처는 완료 7일 뒤 숨겨진다.")
	@PatchMapping("/rentals/{rentalId}/complete")
	public SuccessResponse<Void> complete(@PathVariable Long rentalId) {
		rentalService.complete(OWNER_ID, rentalId);
		return SuccessResponse.ok();
	}

	@Operation(summary = "대여 신청 취소 (RENT-450)", description = "수락 전, 신청자만 가능. 수락 후 취소는 아직 지원하지 않는다.")
	@PatchMapping("/rentals/{rentalId}/cancel")
	public SuccessResponse<Void> cancel(
			@PathVariable Long rentalId) {
		rentalService.cancel(BORROWER_ID, rentalId);
		return SuccessResponse.ok();
	}

}
