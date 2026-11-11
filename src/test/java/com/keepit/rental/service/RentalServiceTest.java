package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.global.config.ClockConfig;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalQuoteResponse;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.RentalStatusLog;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.rental.repository.RentalStatusLogRepository;
import com.keepit.rental.repository.RentalSupportRepository;
import com.keepit.user.entity.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentalServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 10, 0);
	private static final Clock CLOCK = Clock.fixed(NOW.atZone(ClockConfig.SEOUL).toInstant(), ClockConfig.SEOUL);
	private static final Long CLOTHING_ID = 10L;

	@Mock
	private RentalRepository rentalRepository;
	@Mock
	private RentalStatusLogRepository statusLogRepository;
	@Mock
	private RentalSupportRepository supportRepository;

	private RentalService rentalService;
	private User owner;
	private User borrower;
	private User otherBorrower;
	private Clothing clothing;

	@BeforeEach
	void setUp() {
		rentalService = new RentalService(rentalRepository, statusLogRepository, supportRepository,
				new RentalSchedulePolicy(), CLOCK);
		owner = user(1L);
		borrower = user(2L);
		otherBorrower = user(3L);
		clothing = Clothing.builder()
				.owner(owner).title("하객룩 원피스").rentalFee(15000)
				.deliveryAvailable(true).deliveryFee(3500)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build();
		ReflectionTestUtils.setField(clothing, "id", CLOTHING_ID);

		when(supportRepository.findUser(2L)).thenReturn(Optional.of(borrower));
		when(supportRepository.findUser(3L)).thenReturn(Optional.of(otherBorrower));
		when(supportRepository.findClothing(CLOTHING_ID)).thenReturn(Optional.of(clothing));
		when(supportRepository.findClothingForUpdate(CLOTHING_ID)).thenReturn(Optional.of(clothing));
		when(supportRepository.findUnavailablePeriods(CLOTHING_ID)).thenReturn(List.of());
		when(rentalRepository.findAllByClothingIdAndStatusIn(eq(CLOTHING_ID), anyCollection())).thenReturn(List.of());
	}

	private static User user(Long id) {
		User user = User.builder().kakaoId("kakao-" + id).nickname("회원" + id).build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static RentalPeriod period(int startDay, int endDay) {
		return new RentalPeriod(LocalDate.of(2026, 11, startDay), LocalDate.of(2026, 11, endDay));
	}

	private Rental requested(Long id, User who, RentalPeriod period, TradeMethod tradeMethod) {
		Rental rental = Rental.request(clothing, who, period, tradeMethod, NOW);
		ReflectionTestUtils.setField(rental, "id", id);
		return rental;
	}

	private Rental accepted(Long id, User who, RentalPeriod period, TradeMethod tradeMethod) {
		Rental rental = requested(id, who, period, tradeMethod);
		rental.accept(NOW);
		return rental;
	}

	private void givenReservations(Rental... rentals) {
		when(rentalRepository.findAllByClothingIdAndStatusIn(CLOTHING_ID, RentalSchedulePolicy.RESERVED_STATUSES))
				.thenReturn(List.of(rentals));
	}

	private void givenPendingRequests(Rental... rentals) {
		when(rentalRepository.findAllByClothingIdAndStatusIn(eq(CLOTHING_ID),
				eq(java.util.EnumSet.of(RentalStatus.REQUESTED)))).thenReturn(List.of(rentals));
	}

	private static void assertError(Runnable action, RentalErrorCode expected) {
		assertThatThrownBy(action::run).extracting("errorCode").isEqualTo(expected);
	}

	@Nested
	class 신청 {

		@Test
		void 신청하면_대여와_처음_이력을_저장한다() {
			rentalService.request(2L, CLOTHING_ID, period(20, 22), TradeMethod.DIRECT, true);

			ArgumentCaptor<Rental> saved = ArgumentCaptor.forClass(Rental.class);
			verify(rentalRepository).save(saved.capture());
			assertThat(saved.getValue().getStatus()).isEqualTo(RentalStatus.REQUESTED);
			assertThat(saved.getValue().getBorrower()).isSameAs(borrower);
			ArgumentCaptor<RentalStatusLog> log = ArgumentCaptor.forClass(RentalStatusLog.class);
			verify(statusLogRepository).save(log.capture());
			assertThat(log.getValue().getToStatus()).isEqualTo(RentalStatus.REQUESTED);
		}

		@Test
		void 이용_규칙에_동의하지_않으면_신청할_수_없다() {
			assertError(() -> rentalService.request(2L, CLOTHING_ID, period(20, 22), TradeMethod.DIRECT, false),
					RentalErrorCode.RULES_NOT_AGREED);
			verify(rentalRepository, never()).save(any());
		}

		@Test
		void 수락된_예약과_겹치면_신청할_수_없다() {
			givenReservations(accepted(100L, otherBorrower, period(20, 22), TradeMethod.DIRECT));

			assertError(() -> rentalService.request(2L, CLOTHING_ID, period(22, 24), TradeMethod.DIRECT, true),
					RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
			verify(rentalRepository, never()).save(any());
		}

		@Test
		void 없는_옷이면_404() {
			when(supportRepository.findClothing(99L)).thenReturn(Optional.empty());

			assertError(() -> rentalService.request(2L, 99L, period(20, 22), TradeMethod.DIRECT, true),
					RentalErrorCode.CLOTHING_NOT_FOUND);
		}

	}

	@Nested
	class 비용_확인 {

		@Test
		void 택배는_대여료에_보내는_택배비를_더하고_반납_택배비는_따로_안내한다() {
			RentalQuoteResponse quote = rentalService.quote(2L, CLOTHING_ID, period(20, 22), TradeMethod.DELIVERY);

			assertThat(quote.rentalFee()).isEqualTo(15000);
			assertThat(quote.deliveryFee()).isEqualTo(3500);
			assertThat(quote.totalFee()).isEqualTo(18500);
			assertThat(quote.returnDeliveryPaidByBorrower()).isTrue();
			assertThat(quote.rentalDays()).isEqualTo(3);
		}

		@Test
		void 직거래는_대여료만_낸다() {
			RentalQuoteResponse quote = rentalService.quote(2L, CLOTHING_ID, period(20, 22), TradeMethod.DIRECT);

			assertThat(quote.deliveryFee()).isNull();
			assertThat(quote.totalFee()).isEqualTo(15000);
			assertThat(quote.returnDeliveryPaidByBorrower()).isFalse();
		}

		@Test
		void 비용_확인은_저장하지_않는다() {
			rentalService.quote(2L, CLOTHING_ID, period(20, 22), TradeMethod.DIRECT);

			verify(rentalRepository, never()).save(any());
			verify(statusLogRepository, never()).save(any());
		}

	}

	@Nested
	class 수락 {

		@Test
		void 수락하면_옷을_잠그고_겹치는_다른_신청만_무효로_만든다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			Rental overlapping = requested(2L, otherBorrower, period(21, 23), TradeMethod.DIRECT);
			Rental tooClose = requested(3L, otherBorrower, period(23, 24), TradeMethod.DIRECT);
			Rental farAway = requested(4L, otherBorrower, period(26, 27), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));
			givenPendingRequests(target, overlapping, tooClose, farAway);

			RentalAcceptResult result = rentalService.accept(1L, 1L);

			verify(supportRepository).findClothingForUpdate(CLOTHING_ID);
			assertThat(target.getStatus()).isEqualTo(RentalStatus.ACCEPTED);
			assertThat(overlapping.getStatus()).isEqualTo(RentalStatus.INVALIDATED);
			assertThat(tooClose.getStatus()).isEqualTo(RentalStatus.INVALIDATED);
			assertThat(farAway.getStatus()).isEqualTo(RentalStatus.REQUESTED);
			assertThat(result.invalidatedRentalIds()).containsExactly(2L, 3L);
			verify(statusLogRepository).saveAll(anyIterable());
		}

		@Test
		void 주인이_아니면_수락할_수_없다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));

			assertError(() -> rentalService.accept(3L, 1L), RentalErrorCode.RENTAL_ACCESS_DENIED);
			verify(supportRepository, never()).findClothingForUpdate(any());
		}

		@Test
		void 그사이_겹치는_예약이_수락됐으면_수락할_수_없다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));
			givenReservations(accepted(9L, otherBorrower, period(22, 23), TradeMethod.DIRECT));

			assertError(() -> rentalService.accept(1L, 1L), RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
			assertThat(target.getStatus()).isEqualTo(RentalStatus.REQUESTED);
		}

		@Test
		void 없는_신청이면_404() {
			when(rentalRepository.findById(5L)).thenReturn(Optional.empty());

			assertError(() -> rentalService.accept(1L, 5L), RentalErrorCode.RENTAL_NOT_FOUND);
		}

	}

	@Nested
	class 거절과_취소 {

		@Test
		void 주인이_거절하면_이력을_저장한다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));

			rentalService.reject(1L, 1L, RejectReason.TOO_FAR, null);

			assertThat(target.getStatus()).isEqualTo(RentalStatus.REJECTED);
			verify(statusLogRepository).save(any(RentalStatusLog.class));
		}

		@Test
		void 신청자는_거절할_수_없다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));

			assertError(() -> rentalService.reject(2L, 1L, RejectReason.TOO_FAR, null),
					RentalErrorCode.RENTAL_ACCESS_DENIED);
		}

		@Test
		void 신청자가_취소한다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));

			rentalService.cancel(2L, 1L);

			assertThat(target.getStatus()).isEqualTo(RentalStatus.CANCELED);
		}

		@Test
		void 주인은_신청자_취소를_할_수_없다() {
			Rental target = requested(1L, borrower, period(20, 22), TradeMethod.DIRECT);
			when(rentalRepository.findById(1L)).thenReturn(Optional.of(target));

			assertError(() -> rentalService.cancel(1L, 1L), RentalErrorCode.RENTAL_ACCESS_DENIED);
		}

	}

	@Nested
	class 대여_가능_일정 {

		@Test
		void 거래_방식별로_막힌_날짜와_사유를_준다() {
			when(supportRepository.findUnavailablePeriods(CLOTHING_ID)).thenReturn(List.of(period(14, 14)));
			givenReservations(accepted(9L, otherBorrower, period(20, 22), TradeMethod.DIRECT));

			RentalAvailabilityResponse response = rentalService.getAvailability(CLOTHING_ID,
					LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 27));

			assertThat(response.directUnavailableDates())
					.extracting(d -> d.date().getDayOfMonth() + ":" + d.reason())
					.containsExactly("14:OWNER_BLOCKED", "19:RESERVED", "20:RESERVED", "21:RESERVED",
							"22:RESERVED", "23:RESERVED");
			// 택배는 앞뒤 2일 배송까지 피해야 해서 더 넓게 막힌다
			assertThat(response.deliveryUnavailableDates())
					.extracting(d -> d.date().getDayOfMonth())
					.containsExactly(12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25);
			assertThat(response.rentable()).isTrue();
			assertThat(response.maxRentalDays()).isEqualTo(7);
		}

		@Test
		void 구간을_안_주면_오늘부터_60일() {
			RentalAvailabilityResponse response = rentalService.getAvailability(CLOTHING_ID, null, null);

			assertThat(response.from()).isEqualTo(LocalDate.of(2026, 11, 11));
			assertThat(response.to()).isEqualTo(LocalDate.of(2027, 1, 9));
		}

		@Test
		void 오늘_이전은_오늘부터_보여준다() {
			RentalAvailabilityResponse response = rentalService.getAvailability(CLOTHING_ID,
					LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20));

			assertThat(response.from()).isEqualTo(LocalDate.of(2026, 11, 11));
		}

		@Test
		void 조회_구간은_최대_92일이다() {
			assertError(() -> rentalService.getAvailability(CLOTHING_ID,
					LocalDate.of(2026, 11, 11), LocalDate.of(2027, 2, 11)),
					RentalErrorCode.AVAILABILITY_RANGE_TOO_LONG);
		}

	}

}
