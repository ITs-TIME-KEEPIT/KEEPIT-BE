package com.keepit.rental.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RentalPeriodTest {

	private static RentalPeriod period(int startDay, int endDay) {
		return new RentalPeriod(LocalDate.of(2026, 11, startDay), LocalDate.of(2026, 11, endDay));
	}

	@Test
	void 일수는_양끝을_포함해서_센다() {
		assertThat(period(20, 20).days()).isEqualTo(1);
		assertThat(period(20, 26).days()).isEqualTo(7);
	}

	@Test
	void 돌려주는_날이_받는_날보다_앞서면_만들_수_없다() {
		assertThatThrownBy(() -> period(21, 20))
				.isInstanceOf(RentalException.class)
				.extracting("errorCode").isEqualTo(RentalErrorCode.INVALID_RENTAL_PERIOD);
	}

	@Test
	void 하루라도_겹치면_겹친다고_판단한다() {
		assertThat(period(20, 22).overlaps(period(22, 25))).isTrue();
		assertThat(period(20, 22).overlaps(period(18, 20))).isTrue();
		assertThat(period(20, 22).overlaps(period(21, 21))).isTrue();
	}

	@Test
	void 맞닿기만_하고_겹치지_않으면_겹치지_않는다() {
		assertThat(period(20, 22).overlaps(period(23, 25))).isFalse();
		assertThat(period(20, 22).overlaps(period(17, 19))).isFalse();
	}

	@Test
	void expand는_앞뒤로_같은_일수만큼_늘린다() {
		assertThat(period(20, 22).expand(2)).isEqualTo(period(18, 24));
	}

}
