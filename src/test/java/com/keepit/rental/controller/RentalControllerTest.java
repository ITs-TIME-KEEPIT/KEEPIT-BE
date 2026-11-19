package com.keepit.rental.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.global.exception.GlobalExceptionHandler;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalDetailResponse;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalNextAction;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import com.keepit.rental.service.RentalService;
import com.keepit.rental.service.UnavailableReason;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RentalControllerTest {

	// 로그인 전 임시 고정 id (11/10 회의 결정). RentalController의 상수와 같아야 한다.
	private static final Long OWNER_ID = 1L;
	private static final Long BORROWER_ID = 2L;

	private final RentalService rentalService = mock(RentalService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new RentalController(rentalService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void 대여_신청은_201과_새_대여_id를_돌려주고_고정된_대여자로_신청한다() throws Exception {
		when(rentalService.request(any(), any(), any(), any(), eq(true))).thenReturn(55L);

		mockMvc.perform(post("/api/v1/clothes/10/rentals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2026-11-20","endDate":"2026-11-22","tradeMethod":"DIRECT","rulesAgreed":true}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS_201"))
				.andExpect(jsonPath("$.data.rentalId").value(55));

		verify(rentalService).request(BORROWER_ID, 10L,
				new RentalPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)), TradeMethod.DIRECT, true);
	}

	@Test
	void 필수값이_없으면_서비스까지_가지_않고_400() throws Exception {
		mockMvc.perform(post("/api/v1/clothes/10/rentals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"endDate":"2026-11-22","tradeMethod":"DIRECT","rulesAgreed":true}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"))
				.andExpect(jsonPath("$.message").value("받는 날을 선택해 주세요."));

		verifyNoInteractions(rentalService);
	}

	@Test
	void 돌려주는_날이_받는_날보다_앞서면_대여_에러_코드로_400() throws Exception {
		mockMvc.perform(post("/api/v1/clothes/10/rental-quotes")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2026-11-22","endDate":"2026-11-20","tradeMethod":"DIRECT"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RENTAL_400_1"));
	}

	@Test
	void 서비스의_대여_예외는_해당_코드와_상태로_응답한다() throws Exception {
		when(rentalService.request(any(), any(), any(), any(), any(Boolean.class)))
				.thenThrow(new RentalException(RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED));

		mockMvc.perform(post("/api/v1/clothes/10/rentals")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"startDate":"2026-11-20","endDate":"2026-11-22","tradeMethod":"DIRECT","rulesAgreed":true}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("RENTAL_409_2"))
				.andExpect(jsonPath("$.message").value("이미 예약된 기간과 겹칩니다."));
	}

	@Test
	void 일정_조회는_날짜_구간을_넘기고_막힌_날짜를_돌려준다() throws Exception {
		LocalDate from = LocalDate.of(2026, 11, 11);
		LocalDate to = LocalDate.of(2026, 11, 30);
		when(rentalService.getAvailability(10L, from, to)).thenReturn(new RentalAvailabilityResponse(
				10L, from, to, true, true, 7,
				List.of(new RentalAvailabilityResponse.UnavailableDate(LocalDate.of(2026, 11, 20),
						UnavailableReason.RESERVED)),
				List.of()));

		mockMvc.perform(get("/api/v1/clothes/10/availability")
						.param("from", "2026-11-11").param("to", "2026-11-30"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.maxRentalDays").value(7))
				.andExpect(jsonPath("$.data.directUnavailableDates[0].date").value("2026-11-20"))
				.andExpect(jsonPath("$.data.directUnavailableDates[0].reason").value("RESERVED"));
	}

	@Test
	void 받은_신청_목록은_고정된_주인과_필터를_넘긴다() throws Exception {
		when(rentalService.getReceivedRequests(OWNER_ID, 10L, RentalStatus.REQUESTED)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/me/rental-requests").param("clothesId", "10").param("status", "REQUESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());

		verify(rentalService).getReceivedRequests(OWNER_ID, 10L, RentalStatus.REQUESTED);
	}

	@Test
	void 수락하면_무효_처리된_신청_id를_함께_돌려준다() throws Exception {
		when(rentalService.accept(OWNER_ID, 1L)).thenReturn(new RentalAcceptResult(1L, List.of(2L, 3L)));

		mockMvc.perform(patch("/api/v1/rentals/1/accept"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.rentalId").value(1))
				.andExpect(jsonPath("$.data.invalidatedRentalIds[1]").value(3));
	}

	@Test
	void 거절은_사유와_직접_입력을_넘긴다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/reject")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason":"ETC","reasonDetail":"일정이 바뀌었어요"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(rentalService).reject(OWNER_ID, 1L, RejectReason.ETC, "일정이 바뀌었어요");
	}

	@Test
	void 거절_사유가_없으면_400() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/reject")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("거절 사유를 선택해 주세요."));
	}

	@Test
	void 취소는_고정된_대여자로_요청한다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/cancel"))
				.andExpect(status().isOk());

		verify(rentalService).cancel(BORROWER_ID, 1L);
	}

	@Test
	void 전달_완료는_고정된_주인으로_요청한다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/handover"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		verify(rentalService).handOver(OWNER_ID, 1L);
	}

	@Test
	void 수령과_반납은_고정된_대여자로_요청한다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/receipt")).andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/rentals/1/return")).andExpect(status().isOk());

		verify(rentalService).receive(BORROWER_ID, 1L);
		verify(rentalService).returnItem(BORROWER_ID, 1L);
	}

	@Test
	void 거래_완료는_고정된_주인으로_요청한다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/complete")).andExpect(status().isOk());

		verify(rentalService).complete(OWNER_ID, 1L);
	}

	@Test
	void 단계를_건너뛰면_서비스의_상태_오류를_그대로_응답한다() throws Exception {
		doThrow(new RentalException(RentalErrorCode.INVALID_RENTAL_STATUS))
				.when(rentalService).complete(OWNER_ID, 1L);

		mockMvc.perform(patch("/api/v1/rentals/1/complete"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RENTAL_409_4"));
	}

	@Test
	void 거래_상세는_조회자_역할과_다음_할_일을_준다() throws Exception {
		when(rentalService.getDetail(OWNER_ID, 1L)).thenReturn(detail());

		mockMvc.perform(get("/api/v1/rentals/1").param("viewerId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.data.nextAction").value("HANDOVER"))
				.andExpect(jsonPath("$.data.nextActionBy").value("OWNER"))
				.andExpect(jsonPath("$.data.viewerRole").value("OWNER"));
	}

	@Test
	void 거래_상세의_조회자는_기본이_대여자다() throws Exception {
		when(rentalService.getDetail(BORROWER_ID, 1L)).thenReturn(detail());

		mockMvc.perform(get("/api/v1/rentals/1")).andExpect(status().isOk());

		verify(rentalService).getDetail(BORROWER_ID, 1L);
	}

	private static RentalDetailResponse detail() {
		return new RentalDetailResponse(1L, RentalStatus.ACCEPTED, RentalNextAction.HANDOVER, ActorType.OWNER,
				ActorType.OWNER, 10L, "하객룩 원피스", 1L, "주인", 2L, "대여자",
				LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22), TradeMethod.DIRECT,
				15000, null, LaundryBy.BORROWER, LaundryMethod.DRY_CLEAN,
				null, null, null, null,
				LocalDateTime.of(2026, 11, 11, 10, 0), LocalDateTime.of(2026, 11, 13, 10, 0),
				LocalDateTime.of(2026, 11, 11, 12, 0), null, null, null, null);
	}

}
