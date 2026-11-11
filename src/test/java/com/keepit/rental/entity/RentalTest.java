package com.keepit.rental.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RentalTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 10, 0);
	private static final RentalPeriod PERIOD =
			new RentalPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22));

	private User owner;
	private User borrower;
	private Clothing clothing;

	@BeforeEach
	void setUp() {
		owner = user(1L, "주인");
		borrower = user(2L, "대여자");
		clothing = clothing(true);
	}

	private static User user(Long id, String nickname) {
		User user = User.builder().kakaoId("kakao-" + id).nickname(nickname).build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Clothing clothing(boolean deliveryAvailable) {
		return Clothing.builder()
				.owner(owner)
				.title("하객룩 원피스")
				.rentalFee(15000)
				.deliveryAvailable(deliveryAvailable)
				.deliveryFee(deliveryAvailable ? 3500 : null)
				.laundryBy(LaundryBy.BORROWER)
				.laundryMethod(LaundryMethod.DRY_CLEAN)
				.build();
	}

	private Rental requested() {
		return Rental.request(clothing, borrower, PERIOD, TradeMethod.DIRECT, NOW);
	}

	private static void assertError(Runnable action, RentalErrorCode expected) {
		assertThatThrownBy(action::run).extracting("responseCode").isEqualTo(expected);
	}

	@Nested
	class 신청 {

		@Test
		void 신청_시점의_조건을_복사하고_48시간_뒤_만료로_잡는다() {
			Rental rental = Rental.request(clothing(true), borrower, PERIOD, TradeMethod.DELIVERY, NOW);

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
			assertThat(rental.getOwner()).isSameAs(owner);
			assertThat(rental.getAppliedRentalFee()).isEqualTo(15000);
			assertThat(rental.getAppliedDeliveryFee()).isEqualTo(3500);
			assertThat(rental.getAppliedLaundryBy()).isEqualTo(LaundryBy.BORROWER);
			assertThat(rental.getAppliedLaundryMethod()).isEqualTo(LaundryMethod.DRY_CLEAN);
			assertThat(rental.getRulesAgreedAt()).isEqualTo(NOW);
			assertThat(rental.getExpiresAt()).isEqualTo(NOW.plusHours(48));
		}

		@Test
		void 직거래면_택배비를_저장하지_않는다() {
			Rental rental = Rental.request(clothing(true), borrower, PERIOD, TradeMethod.DIRECT, NOW);

			assertThat(rental.getAppliedDeliveryFee()).isNull();
		}

		@Test
		void 처음_이력은_대여자가_만든_신청으로_남는다() {
			RentalStatusLog log = requested().createdLog();

			assertThat(log.getFromStatus()).isNull();
			assertThat(log.getToStatus()).isEqualTo(RentalStatus.REQUESTED);
			assertThat(log.getActorType()).isEqualTo(ActorType.BORROWER);
			assertThat(log.getActor()).isSameAs(borrower);
		}

		@Test
		void 내_옷은_빌릴_수_없다() {
			assertError(() -> Rental.request(clothing, owner, PERIOD, TradeMethod.DIRECT, NOW),
					RentalErrorCode.CANNOT_RENT_OWN_CLOTHING);
		}

		@Test
		void 택배_불가_옷은_택배로_신청할_수_없다() {
			assertError(() -> Rental.request(clothing(false), borrower, PERIOD, TradeMethod.DELIVERY, NOW),
					RentalErrorCode.DELIVERY_NOT_AVAILABLE);
		}

		@Test
		void 대여_중지된_옷은_신청할_수_없다() {
			ReflectionTestUtils.setField(clothing, "status", ClothingStatus.PAUSED);

			assertError(() -> requested(), RentalErrorCode.CLOTHING_NOT_RENTABLE);
		}

		@Test
		void 삭제된_옷은_신청할_수_없다() {
			ReflectionTestUtils.setField(clothing, "deletedAt", NOW.minusDays(1));

			assertError(() -> requested(), RentalErrorCode.CLOTHING_NOT_RENTABLE);
		}

	}

	@Nested
	class 수락 {

		@Test
		void 수락하면_상태와_수락_시각이_바뀌고_주인_이력이_남는다() {
			Rental rental = requested();

			RentalStatusLog log = rental.accept(NOW.plusHours(1));

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.ACCEPTED);
			assertThat(rental.getAcceptedAt()).isEqualTo(NOW.plusHours(1));
			assertThat(log.getFromStatus()).isEqualTo(RentalStatus.REQUESTED);
			assertThat(log.getToStatus()).isEqualTo(RentalStatus.ACCEPTED);
			assertThat(log.getActorType()).isEqualTo(ActorType.OWNER);
			assertThat(log.getActor()).isSameAs(owner);
		}

		@Test
		void 정확히_48시간까지는_수락할_수_있다() {
			Rental rental = requested();

			rental.accept(NOW.plusHours(48));

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.ACCEPTED);
		}

		@Test
		void 응답_기한_48시간이_지나면_수락할_수_없다() {
			Rental rental = requested();

			assertError(() -> rental.accept(NOW.plusHours(48).plusSeconds(1)),
					RentalErrorCode.RENTAL_REQUEST_EXPIRED);
			assertThat(rental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
		}

		@Test
		void 이미_처리된_신청은_다시_수락할_수_없다() {
			Rental rental = requested();
			rental.accept(NOW);

			assertError(() -> rental.accept(NOW), RentalErrorCode.INVALID_RENTAL_STATUS);
		}

	}

	@Nested
	class 거절 {

		@Test
		void 사유를_골라_거절한다() {
			Rental rental = requested();

			RentalStatusLog log = rental.reject(RejectReason.OWNER_USE, "무시되는 입력");

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.REJECTED);
			assertThat(rental.getRejectReason()).isEqualTo(RejectReason.OWNER_USE);
			assertThat(rental.getRejectReasonDetail()).isNull();
			assertThat(log.getActorType()).isEqualTo(ActorType.OWNER);
		}

		@Test
		void 기타_사유는_직접_입력을_저장한다() {
			Rental rental = requested();

			rental.reject(RejectReason.ETC, "  일정이 바뀌었어요 ");

			assertThat(rental.getRejectReasonDetail()).isEqualTo("일정이 바뀌었어요");
		}

		@Test
		void 사유가_없으면_거절할_수_없다() {
			assertError(() -> requested().reject(null, null), RentalErrorCode.REJECT_REASON_REQUIRED);
		}

		@Test
		void 기타인데_입력이_비어_있으면_거절할_수_없다() {
			assertError(() -> requested().reject(RejectReason.ETC, " "),
					RentalErrorCode.REJECT_REASON_DETAIL_REQUIRED);
		}

	}

	@Nested
	class 취소와_무효 {

		@Test
		void 대여자는_수락_전에_취소할_수_있다() {
			Rental rental = requested();

			RentalStatusLog log = rental.cancelByBorrower();

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
			assertThat(rental.getCanceledBy()).isEqualTo(ActorType.BORROWER);
			assertThat(log.getActor()).isSameAs(borrower);
		}

		@Test
		void 수락_후에는_대여자_취소를_아직_지원하지_않는다() {
			Rental rental = requested();
			rental.accept(NOW);

			assertError(rental::cancelByBorrower, RentalErrorCode.INVALID_RENTAL_STATUS);
		}

		@Test
		void 무효_처리는_시스템_이력으로_남는다() {
			Rental rental = requested();

			RentalStatusLog log = rental.invalidate();

			assertThat(rental.getStatus()).isEqualTo(RentalStatus.INVALIDATED);
			assertThat(log.getActorType()).isEqualTo(ActorType.SYSTEM);
			assertThat(log.getActor()).isNull();
			assertThat(log.getReason()).isNotBlank();
		}

	}

	@Test
	void 주인과_대여자를_id로_구분한다() {
		Rental rental = requested();

		assertThat(rental.isOwnedBy(1L)).isTrue();
		assertThat(rental.isBorrowedBy(2L)).isTrue();
		assertThat(rental.isOwnedBy(2L)).isFalse();
		assertThat(rental.isBorrowedBy(3L)).isFalse();
	}

}
