package com.keepit.rental.entity;

import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 대여 기간 (받는 날 ~ 돌려주는 날, 양끝 포함).
 */
public record RentalPeriod(LocalDate startDate, LocalDate endDate) {

	public RentalPeriod {
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		if (endDate.isBefore(startDate)) {
			throw new RentalException(RentalErrorCode.INVALID_RENTAL_PERIOD);
		}
	}

	/** 양끝을 포함한 일수. 11/20 ~ 11/20 이면 1일. */
	public long days() {
		return ChronoUnit.DAYS.between(startDate, endDate) + 1;
	}

	/** 앞뒤로 days일씩 늘린 기간. */
	public RentalPeriod expand(long days) {
		return new RentalPeriod(startDate.minusDays(days), endDate.plusDays(days));
	}

	/** 하루라도 겹치면 true. */
	public boolean overlaps(RentalPeriod other) {
		return !startDate.isAfter(other.endDate) && !other.startDate.isAfter(endDate);
	}

}
