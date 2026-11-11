package com.keepit.rental.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import com.keepit.rental.service.RentalService;
import com.keepit.rental.service.UnavailableReason;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RentalControllerTest {

	private static final Long LOGIN_USER_ID = 7L;

	private final RentalService rentalService = mock(RentalService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new RentalController(rentalService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.build();
		// JWT 필터가 하는 일: principal에 회원 id를 넣는다
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void 대여_신청은_201과_새_대여_id를_돌려주고_로그인한_회원으로_신청한다() throws Exception {
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

		verify(rentalService).request(LOGIN_USER_ID, 10L,
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
	void 받은_신청_목록은_로그인한_주인과_필터를_넘긴다() throws Exception {
		when(rentalService.getReceivedRequests(LOGIN_USER_ID, 10L, RentalStatus.REQUESTED)).thenReturn(List.of());

		mockMvc.perform(get("/api/v1/me/rental-requests").param("clothesId", "10").param("status", "REQUESTED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray());

		verify(rentalService).getReceivedRequests(LOGIN_USER_ID, 10L, RentalStatus.REQUESTED);
	}

	@Test
	void 수락하면_무효_처리된_신청_id를_함께_돌려준다() throws Exception {
		when(rentalService.accept(LOGIN_USER_ID, 1L)).thenReturn(new RentalAcceptResult(1L, List.of(2L, 3L)));

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

		verify(rentalService).reject(LOGIN_USER_ID, 1L, RejectReason.ETC, "일정이 바뀌었어요");
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
	void 취소는_로그인한_회원으로_요청한다() throws Exception {
		mockMvc.perform(patch("/api/v1/rentals/1/cancel"))
				.andExpect(status().isOk());

		verify(rentalService).cancel(LOGIN_USER_ID, 1L);
	}

}
