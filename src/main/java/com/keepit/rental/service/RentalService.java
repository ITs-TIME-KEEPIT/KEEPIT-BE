package com.keepit.rental.service;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.rental.dto.ReceivedRentalRequestResponse;
import com.keepit.rental.dto.RentalAcceptResult;
import com.keepit.rental.dto.RentalAvailabilityResponse;
import com.keepit.rental.dto.RentalDetailResponse;
import com.keepit.rental.dto.RentalAvailabilityResponse.UnavailableDate;
import com.keepit.rental.dto.RentalQuoteResponse;
import com.keepit.rental.entity.ActorType;
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
import org.springframework.transaction.annotation.Isolation;
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

	/**
	 * 대여 신청. 같은 기간 중복 신청은 허용하고, 이미 수락된 예약과 겹치면 막는다.
	 * 수락과 같은 옷 잠금을 먼저 잡는다. 안 잡으면 "날짜 검사 ~ 저장" 사이에 다른 신청이 수락될 때
	 * 그 수락이 아직 저장 안 된 이 신청을 못 찾아 무효 처리에서 빠진다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Long request(Long borrowerId, Long clothingId, RentalPeriod period, TradeMethod tradeMethod,
			boolean rulesAgreed) {
		if (!rulesAgreed) {
			throw new RentalException(RentalErrorCode.RULES_NOT_AGREED);
		}
		supportRepository.findClothingForUpdate(clothingId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.CLOTHING_NOT_FOUND));
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
	 * 1) 옷 행을 잠그고 신청을 최신 상태로 읽는다({@link #lockClothingAndGetRental}).
	 * 2) 잠근 뒤 수락된 예약과 겹치는지 다시 본다(신청 때 비어 있던 날이 그사이 찼을 수 있다).
	 * 3) 이 예약과 함께 잡힐 수 없는 다른 신청은 무효(INVALIDATED)로 만든다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public RentalAcceptResult accept(Long ownerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		Long clothingId = rental.getClothing().getId();

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

	/** 옷 주인이 사유를 골라 거절한다. 같은 옷의 수락과 겹치지 않게 수락과 같은 순서로 잠근다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void reject(Long ownerId, Long rentalId, RejectReason reason, String reasonDetail) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.reject(reason, reasonDetail));
	}

	/** 수락 전 신청자가 취소한다. 주인의 수락과 동시에 들어와도 한쪽만 반영되게 같은 순서로 잠근다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void cancel(Long borrowerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isBorrowedBy(borrowerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.cancelByBorrower());
	}

	/** 옷 주인이 옷을 넘겼다고 표시한다 (TRD-510). */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void handOver(Long ownerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.handOver(LocalDateTime.now(clock)));
	}

	/** 대여자가 옷을 받았다고 확인한다 (TRD-520). */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void receive(Long borrowerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isBorrowedBy(borrowerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.receive(LocalDateTime.now(clock)));
	}

	/** 대여자가 옷을 돌려준다 (TRD-530). */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void returnItem(Long borrowerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isBorrowedBy(borrowerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.returnItem(LocalDateTime.now(clock)));
	}

	/** 옷 주인이 반납을 확인하고 거래를 끝낸다 (TRD-540). */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void complete(Long ownerId, Long rentalId) {
		Rental rental = lockClothingAndGetRental(rentalId);
		if (!rental.isOwnedBy(ownerId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		statusLogRepository.save(rental.complete(LocalDateTime.now(clock)));
	}

	/** 거래 상세 (TRD-501). 거래 당사자(주인·대여자)만 볼 수 있다. */
	public RentalDetailResponse getDetail(Long userId, Long rentalId) {
		Rental rental = getRental(rentalId);
		if (rental.isOwnedBy(userId)) {
			return RentalDetailResponse.of(rental, ActorType.OWNER);
		}
		if (rental.isBorrowedBy(userId)) {
			return RentalDetailResponse.of(rental, ActorType.BORROWER);
		}
		throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
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

	/**
	 * 신청의 상태를 바꾸기 전에 부른다. 순서가 중요하다.
	 * 1) 옷 id만 조회한다(신청 엔티티는 아직 읽지 않는다).
	 * 2) 옷 행을 SELECT ... FOR UPDATE로 잠근다. 같은 옷의 다른 수락·거절·취소는 여기서 기다린다.
	 * 3) 잠금을 얻은 뒤에 신청을 읽는다. READ COMMITTED라 앞 트랜잭션이 커밋한 최신 상태가 보인다.
	 * 잠금 전에 신청을 읽어 두면(또는 REPEATABLE READ의 스냅샷을 보면) 이미 무효가 된 신청을
	 * 수락으로 덮어쓰는 문제가 생긴다 — RentalAcceptConcurrencyTest가 이 경우를 확인한다.
	 */
	private Rental lockClothingAndGetRental(Long rentalId) {
		Long clothingId = rentalRepository.findClothingIdById(rentalId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.RENTAL_NOT_FOUND));
		supportRepository.findClothingForUpdate(clothingId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.CLOTHING_NOT_FOUND));
		return getRental(rentalId);
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
