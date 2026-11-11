package com.keepit.rental.service;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.rental.dto.ReceivedRentalRequestResponse;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalAvailabilityResponse.UnavailableDate;
import com.keepit.rental.dto.RentalQuoteResponse;
import com.keepit.rental.entity.RejectReason;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.RentalStatusLog;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.rental.repository.RentalStatusLogRepository;
import com.keepit.rental.repository.RentalSupportRepository;
import com.keepit.user.entity.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대여 신청부터 수락·거절·취소까지 (RENT-410~450).
 * 규칙 판단은 {@link Rental}(상태)과 {@link RentalSchedulePolicy}(날짜)에 맡기고,
 * 여기서는 필요한 데이터를 모으고, 권한을 확인하고, 결과와 이력을 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalService {

	/** 일정 조회 기본 구간: 오늘부터 60일. */
	static final int DEFAULT_AVAILABILITY_DAYS = 60;
	static final int MAX_AVAILABILITY_DAYS = 92;

	private final RentalRepository rentalRepository;
	private final RentalStatusLogRepository statusLogRepository;
	private final RentalSupportRepository supportRepository;
	private final RentalSchedulePolicy schedulePolicy;
	private final Clock clock;

	/** 대여 가능 일정. from·to를 안 주면 오늘부터 60일. 오늘 이전은 보여주지 않는다. */
	public RentalAvailabilityResponse getAvailability(Long clothingId, LocalDate from, LocalDate to) {
		Clothing clothing = getClothing(clothingId);
		LocalDate today = LocalDate.now(clock);
		LocalDate start = (from == null || from.isBefore(today)) ? today : from;
		LocalDate end = to == null ? start.plusDays(DEFAULT_AVAILABILITY_DAYS - 1) : to;
		RentalPeriod range = new RentalPeriod(start, end);
		if (range.days() > MAX_AVAILABILITY_DAYS) {
			throw new RentalException(RentalErrorCode.AVAILABILITY_RANGE_TOO_LONG);
		}

		List<RentalPeriod> unavailablePeriods = supportRepository.findUnavailablePeriods(clothingId);
		List<Rental> reservations = findReservations(clothingId);
		List<UnavailableDate> direct = new ArrayList<>();
		List<UnavailableDate> delivery = new ArrayList<>();
		for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
			LocalDate d = day;
			schedulePolicy.unavailableReason(d, TradeMethod.DIRECT, unavailablePeriods, reservations)
					.ifPresent(reason -> direct.add(new UnavailableDate(d, reason)));
			schedulePolicy.unavailableReason(d, TradeMethod.DELIVERY, unavailablePeriods, reservations)
					.ifPresent(reason -> delivery.add(new UnavailableDate(d, reason)));
		}

		boolean rentable = clothing.getStatus() == ClothingStatus.AVAILABLE && clothing.getDeletedAt() == null;
		return new RentalAvailabilityResponse(clothingId, start, end, rentable, clothing.isDeliveryAvailable(),
				RentalSchedulePolicy.MAX_RENTAL_DAYS, direct, delivery);
	}

	/** 신청 전 비용·조건 확인. 신청과 같은 검증을 거치고, 저장은 하지 않는다. */
	public RentalQuoteResponse quote(Long borrowerId, Long clothingId, RentalPeriod period, TradeMethod tradeMethod) {
		Rental draft = buildValidatedRequest(borrowerId, clothingId, period, tradeMethod);
		return RentalQuoteResponse.from(draft);
	}

	/** 대여 신청. 같은 기간 중복 신청은 허용하고, 이미 수락된 예약과 겹치면 막는다. */
	@Transactional
	public Long request(Long borrowerId, Long clothingId, RentalPeriod period, TradeMethod tradeMethod,
			boolean rulesAgreed) {
		if (!rulesAgreed) {
			throw new RentalException(RentalErrorCode.RULES_NOT_AGREED);
		}
		Rental rental = buildValidatedRequest(borrowerId, clothingId, period, tradeMethod);
		rentalRepository.save(rental);
		statusLogRepository.save(rental.createdLog());
		return rental.getId();
	}

	/** 내 옷에 들어온 신청 목록. clothingId·status가 null이면 전체. */
	public List<ReceivedRentalRequestResponse> getReceivedRequests(Long ownerId, Long clothingId,
			RentalStatus status) {
		return rentalRepository.findReceivedRequests(ownerId, clothingId, status).stream()
				.map(ReceivedRentalRequestResponse::from)
				.toList();
	}

	/**
	 * 옷 주인이 수락한다.
	 * 1) 옷 행을 잠가 같은 옷의 수락을 한 번에 하나씩만 처리한다.
	 * 2) 잠근 뒤 수락된 예약과 겹치는지 다시 본다(신청 때 비어 있던 날이 그사이 찼을 수 있다).
	 * 3) 이 예약과 함께 잡힐 수 없는 다른 신청은 무효(INVALIDATED)로 만든다.
	 */
	@Transactional
	public RentalAcceptResult accept(Long ownerId, Long rentalId) {
		Rental rental = getRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		Long clothingId = rental.getClothing().getId();
		supportRepository.findClothingForUpdate(clothingId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.CLOTHING_NOT_FOUND));

		if (schedulePolicy.isReserved(rental.getPeriod(), rental.getTradeMethod(), findReservations(clothingId))) {
			throw new RentalException(RentalErrorCode.RENTAL_PERIOD_ALREADY_RESERVED);
		}

		List<RentalStatusLog> logs = new ArrayList<>();
		logs.add(rental.accept(LocalDateTime.now(clock)));

		List<Long> invalidatedIds = new ArrayList<>();
		for (Rental other : rentalRepository.findAllByClothingIdAndStatusIn(clothingId,
				EnumSet.of(RentalStatus.REQUESTED))) {
			if (other.getId().equals(rental.getId())) {
				continue;
			}
			if (schedulePolicy.conflicts(rental.getPeriod(), rental.getTradeMethod(),
					other.getPeriod(), other.getTradeMethod())) {
				logs.add(other.invalidate());
				invalidatedIds.add(other.getId());
			}
		}
		statusLogRepository.saveAll(logs);
		return new RentalAcceptResult(rental.getId(), invalidatedIds);
	}

	/** 옷 주인이 사유를 골라 거절한다. */
	@Transactional
	public void reject(Long ownerId, Long rentalId, RejectReason reason, String reasonDetail) {
		Rental rental = getRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.reject(reason, reasonDetail));
	}

	/** 수락 전 신청자가 취소한다. */
	@Transactional
	public void cancel(Long borrowerId, Long rentalId) {
		Rental rental = getRental(rentalId);
		if (!rental.isBorrowedBy(borrowerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.cancelByBorrower());
	}

	/** 신청 객체를 만들고 날짜 규칙까지 검증한다. 신청과 비용 확인이 같이 쓴다. */
	private Rental buildValidatedRequest(Long borrowerId, Long clothingId, RentalPeriod period,
			TradeMethod tradeMethod) {
		User borrower = supportRepository.findUser(borrowerId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.USER_NOT_FOUND));
		Clothing clothing = getClothing(clothingId);
		Rental rental = Rental.request(clothing, borrower, period, tradeMethod, LocalDateTime.now(clock));
		schedulePolicy.validateRequest(period, tradeMethod, LocalDate.now(clock),
				supportRepository.findUnavailablePeriods(clothingId), findReservations(clothingId));
		return rental;
	}

	private List<Rental> findReservations(Long clothingId) {
		return rentalRepository.findAllByClothingIdAndStatusIn(clothingId, RentalSchedulePolicy.RESERVED_STATUSES);
	}

	private Rental getRental(Long rentalId) {
		return rentalRepository.findById(rentalId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.RENTAL_NOT_FOUND));
	}

	private Clothing getClothing(Long clothingId) {
		return supportRepository.findClothing(clothingId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.CLOTHING_NOT_FOUND));
	}

}
