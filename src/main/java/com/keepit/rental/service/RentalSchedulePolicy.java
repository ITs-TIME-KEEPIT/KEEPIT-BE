package com.keepit.rental.service;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 대여 일정 규칙 (기능명세 RENT-410, COM-006).
 * <ul>
 *   <li>대여 기간은 받는 날~돌려주는 날 양끝 포함 최대 7일</li>
 *   <li>오늘 이전 날짜는 선택 불가</li>
 *   <li>택배 거래는 배송 기간으로 대여 기간 앞뒤 2일을 더 차지한다</li>
 *   <li>예약과 예약 사이에는 최소 1일 간격(세탁·점검)</li>
 *   <li>같은 기간 중복 신청은 허용하고, 수락된 예약만 날짜를 막는다</li>
 * </ul>
 * 버퍼 2일·간격 1일은 기능명세상 초안 값이다.
 */
@Component
public class RentalSchedulePolicy {

	public static final int MAX_RENTAL_DAYS = 7;
	public static final int DELIVERY_BUFFER_DAYS = 2;
	public static final int GAP_DAYS = 1;

	/** 날짜를 막는 상태: 수락 이후 반납 확인 전까지. 완료되면 옷이 돌아왔으므로 막지 않는다. */
	public static final Set<RentalStatus> RESERVED_STATUSES = EnumSet.of(
			RentalStatus.ACCEPTED, RentalStatus.HANDED_OVER, RentalStatus.RECEIVED, RentalStatus.RETURNED);

	/**
	 * 새 신청의 기간을 검증한다.
	 *
	 * @param unavailablePeriods 옷 주인이 막아 둔 기간
	 * @param reservations       같은 옷의 다른 대여 건 (상태와 상관없이 넘겨도 예약 상태만 본다)
	 */
	public void validateRequest(RentalPeriod period, TradeMethod tradeMethod, LocalDate today,
			Collection<RentalPeriod> unavailablePeriods, Collection<Rental> reservations) {
		if (period.startDate().isBefore(today)) {
			throw new RentalException(RentalErrorCode.RENTAL_START_DATE_IN_PAST);
		}
		if (period.days() > MAX_RENTAL_DAYS) {
			throw new RentalException(RentalErrorCode.RENTAL_PERIOD_TOO_LONG);
		}
		if (isOwnerBlocked(period, tradeMethod, unavailablePeriods)) {
			throw new RentalException(RentalErrorCode.RENTAL_PERIOD_UNAVAILABLE);
		}
		if (isReserved(period, tradeMethod, reservations)) {
			throw new RentalException(RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
		}
	}

	/**
	 * 하루를 대여 기간에 넣을 수 없는 이유. 넣을 수 있으면 empty.
	 * 기간이 막히는지는 기간 안의 날짜 중 하루라도 막히는지와 같아서, 달력은 날짜별로 판단하면 된다.
	 */
	public Optional<UnavailableReason> unavailableReason(LocalDate day, TradeMethod tradeMethod,
			Collection<RentalPeriod> unavailablePeriods, Collection<Rental> reservations) {
		RentalPeriod oneDay = new RentalPeriod(day, day);
		if (isOwnerBlocked(oneDay, tradeMethod, unavailablePeriods)) {
			return Optional.of(UnavailableReason.OWNER_BLOCKED);
		}
		if (isReserved(oneDay, tradeMethod, reservations)) {
			return Optional.of(UnavailableReason.RESERVED);
		}
		return Optional.empty();
	}

	/** 옷 주인이 막아 둔 기간과 겹치는지. 택배면 배송 기간까지 본다. */
	public boolean isOwnerBlocked(RentalPeriod period, TradeMethod tradeMethod,
			Collection<RentalPeriod> unavailablePeriods) {
		RentalPeriod occupied = occupiedPeriod(period, tradeMethod);
		return unavailablePeriods.stream().anyMatch(occupied::overlaps);
	}

	/** 이미 수락된 예약과 함께 잡힐 수 없는지. 예약 상태가 아닌 대여 건은 무시한다. */
	public boolean isReserved(RentalPeriod period, TradeMethod tradeMethod, Collection<Rental> reservations) {
		return reservations.stream()
				.filter(rental -> RESERVED_STATUSES.contains(rental.getStatus()))
				.anyMatch(rental -> conflicts(period, tradeMethod, rental.getPeriod(), rental.getTradeMethod()));
	}

	/**
	 * 두 대여가 같은 옷에 함께 잡힐 수 없으면 true.
	 * 각자 실제로 옷을 차지하는 기간(택배면 앞뒤 버퍼 포함) 사이에 최소 GAP_DAYS일이 비어 있어야 한다.
	 */
	public boolean conflicts(RentalPeriod period, TradeMethod tradeMethod,
			RentalPeriod otherPeriod, TradeMethod otherTradeMethod) {
		return occupiedPeriod(period, tradeMethod).expand(GAP_DAYS)
				.overlaps(occupiedPeriod(otherPeriod, otherTradeMethod));
	}

	/** 옷이 실제로 묶이는 기간. 택배는 보내고 돌려받는 시간만큼 앞뒤로 늘어난다. */
	public RentalPeriod occupiedPeriod(RentalPeriod period, TradeMethod tradeMethod) {
		return tradeMethod == TradeMethod.DELIVERY ? period.expand(DELIVERY_BUFFER_DAYS) : period;
	}

}
