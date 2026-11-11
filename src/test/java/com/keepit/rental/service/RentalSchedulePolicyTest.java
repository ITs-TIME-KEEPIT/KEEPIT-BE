package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RentalSchedulePolicyTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);
	private static final LocalDateTime NOW = TODAY.atTime(10, 0);

	private final RentalSchedulePolicy policy = new RentalSchedulePolicy();

	private static RentalPeriod period(int startDay, int endDay) {
		return new RentalPeriod(LocalDate.of(2026, 11, startDay), LocalDate.of(2026, 11, endDay));
	}

	private static Rental requested(RentalPeriod period, TradeMethod tradeMethod) {
		return Rental.builder()
				.startDate(period.startDate())
				.endDate(period.endDate())
				.tradeMethod(tradeMethod)
				.requestedAt(NOW)
				.expiresAt(NOW.plus(Rental.RESPONSE_DEADLINE))
				.build();
	}

	private static Rental accepted(RentalPeriod period, TradeMethod tradeMethod) {
		Rental rental = requested(period, tradeMethod);
		rental.accept(NOW);
		return rental;
	}

	private void assertRejected(RentalPeriod period, TradeMethod method, List<RentalPeriod> unavailable,
			List<Rental> reservations, RentalErrorCode expected) {
		assertThatThrownBy(() -> policy.validateRequest(period, method, TODAY, unavailable, reservations))
				.extracting("responseCode").isEqualTo(expected);
	}

	private void assertAllowed(RentalPeriod period, TradeMethod method, List<RentalPeriod> unavailable,
			List<Rental> reservations) {
		assertThatCode(() -> policy.validateRequest(period, method, TODAY, unavailable, reservations))
				.doesNotThrowAnyException();
	}

	@Nested
	class 기간_자체_검증 {

		@Test
		void 오늘부터는_신청할_수_있다() {
			assertAllowed(period(11, 13), TradeMethod.DIRECT, List.of(), List.of());
		}

		@Test
		void 오늘_이전에_시작하면_안된다() {
			assertRejected(period(10, 12), TradeMethod.DIRECT, List.of(), List.of(),
					RentalErrorCode.RENTAL_START_DATE_IN_PAST);
		}

		@Test
		void 양끝_포함_7일까지는_된다() {
			assertAllowed(period(20, 26), TradeMethod.DIRECT, List.of(), List.of());
		}

		@Test
		void 양끝_포함_8일부터는_너무_길다() {
			assertRejected(period(20, 27), TradeMethod.DIRECT, List.of(), List.of(),
					RentalErrorCode.RENTAL_PERIOD_TOO_LONG);
		}

	}

	@Nested
	class 옷_주인이_막아둔_기간 {

		@Test
		void 직거래는_막힌_기간과_겹칠_때만_안된다() {
			List<RentalPeriod> blocked = List.of(period(25, 26));
			assertRejected(period(24, 25), TradeMethod.DIRECT, blocked, List.of(),
					RentalErrorCode.RENTAL_PERIOD_UNAVAILABLE);
			assertAllowed(period(22, 24), TradeMethod.DIRECT, blocked, List.of());
		}

		@Test
		void 택배는_앞뒤_배송_2일까지_겹치는지_본다() {
			// 22~24 택배는 실제로 20~26을 차지하므로 25~26 막힌 기간과 겹친다
			assertRejected(period(22, 24), TradeMethod.DELIVERY, List.of(period(25, 26)), List.of(),
					RentalErrorCode.RENTAL_PERIOD_UNAVAILABLE);
		}

	}

	@Nested
	class 이미_수락된_예약 {

		@Test
		void 직거래끼리는_사이에_하루가_비어야_한다() {
			List<Rental> reservations = List.of(accepted(period(20, 22), TradeMethod.DIRECT));
			// 22일 반납 → 23일은 세탁·점검 → 24일부터 가능
			assertRejected(period(23, 25), TradeMethod.DIRECT, List.of(), reservations,
					RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
			assertAllowed(period(24, 25), TradeMethod.DIRECT, List.of(), reservations);
			// 앞쪽도 마찬가지: 18일 반납 → 19일 비움 → 20일 시작 가능
			assertAllowed(period(16, 18), TradeMethod.DIRECT, List.of(), reservations);
			assertRejected(period(16, 19), TradeMethod.DIRECT, List.of(), reservations,
					RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
		}

		@Test
		void 기존_예약이_택배면_그_배송_기간까지_피해야_한다() {
			// 20~22 택배는 18~24를 차지 → 25일 비움 → 26일부터 직거래 가능
			List<Rental> reservations = List.of(accepted(period(20, 22), TradeMethod.DELIVERY));
			assertRejected(period(25, 25), TradeMethod.DIRECT, List.of(), reservations,
					RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
			assertAllowed(period(26, 27), TradeMethod.DIRECT, List.of(), reservations);
		}

		@Test
		void 새_신청이_택배면_새_신청의_배송_기간도_더한다() {
			// 기존 20~22 직거래 → 23일 비움 → 새 택배는 24일부터 차지해야 하므로 받는 날은 26일부터
			List<Rental> reservations = List.of(accepted(period(20, 22), TradeMethod.DIRECT));
			assertRejected(period(25, 26), TradeMethod.DELIVERY, List.of(), reservations,
					RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
			assertAllowed(period(26, 27), TradeMethod.DELIVERY, List.of(), reservations);
		}

		@Test
		void 아직_수락되지_않은_신청은_날짜를_막지_않는다() {
			List<Rental> reservations = List.of(requested(period(20, 22), TradeMethod.DIRECT));
			assertAllowed(period(20, 22), TradeMethod.DIRECT, List.of(), reservations);
		}

	}

	@Test
	void conflicts는_양쪽_방향으로_같은_결과를_낸다() {
		RentalPeriod a = period(20, 22);
		RentalPeriod b = period(24, 25);
		assertThat(policy.conflicts(a, TradeMethod.DELIVERY, b, TradeMethod.DIRECT))
				.isEqualTo(policy.conflicts(b, TradeMethod.DIRECT, a, TradeMethod.DELIVERY))
				.isTrue();
	}

}
