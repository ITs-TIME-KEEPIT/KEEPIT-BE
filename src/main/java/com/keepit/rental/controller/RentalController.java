package com.keepit.rental.controller;

import com.keepit.global.response.SuccessResponse;
import com.keepit.rental.dto.ReceivedRentalRequestResponse;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalCreateRequest;
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
 * 대여 신청·수락·거절·취소 API (RENT-410~450).
 * 로그인한 사용자 id는 JWT 필터가 넣어 준 인증 정보(principal = 회원 id)에서 꺼낸다.
 */
@Tag(name = "Rental", description = "대여 신청·수락·거절·취소")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RentalController {

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

	@Operation(summary = "대여 신청 취소 (RENT-450)", description = "수락 전, 신청자만 가능. 수락 후 취소는 아직 지원하지 않는다.")
	@PatchMapping("/rentals/{rentalId}/cancel")
	public SuccessResponse<Void> cancel(
			@AuthenticationPrincipal Long userId,
			@PathVariable Long rentalId) {
		rentalService.cancel(userId, rentalId);
		return SuccessResponse.ok();
	}

}
