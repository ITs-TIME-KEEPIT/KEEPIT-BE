package com.keepit.rental.entity;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.global.entity.BaseTimeEntity;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import com.keepit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대여 건. applied_* 컬럼은 신청 시점의 의류 조건 스냅샷이고,
 * owner는 조회 편의를 위해 clothes.owner_id를 비정규화해 둔 값이다.
 * 상태 전이 이력은 {@link RentalStatusLog}에 남긴다.
 */
@Getter
@Entity
@Table(name = "rentals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rental extends BaseTimeEntity {

	/** 신청 후 옷 주인이 응답해야 하는 기한 (RENT-440). */
	public static final Duration RESPONSE_DEADLINE = Duration.ofHours(48);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "clothing_id", nullable = false)
	private Clothing clothing;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "borrower_id", nullable = false)
	private User borrower;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "trade_method", nullable = false, length = 30)
	private TradeMethod tradeMethod;

	@Column(name = "applied_rental_fee", nullable = false)
	private int appliedRentalFee;

	@Column(name = "applied_delivery_fee")
	private Integer appliedDeliveryFee;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "applied_laundry_by", nullable = false, length = 30)
	private LaundryBy appliedLaundryBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "applied_laundry_method", nullable = false, length = 30)
	private LaundryMethod appliedLaundryMethod;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private RentalStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "reject_reason", length = 30)
	private RejectReason rejectReason;

	@Column(name = "reject_reason_detail")
	private String rejectReasonDetail;

	@Column(name = "cancel_reason")
	private String cancelReason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "canceled_by", length = 30)
	private ActorType canceledBy;

	@Column(name = "rules_agreed_at", nullable = false)
	private LocalDateTime rulesAgreedAt;

	@Column(name = "requested_at", nullable = false)
	private LocalDateTime requestedAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;

	@Column(name = "handed_over_at")
	private LocalDateTime handedOverAt;

	@Column(name = "received_at")
	private LocalDateTime receivedAt;

	@Column(name = "returned_at")
	private LocalDateTime returnedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "phone_hidden_at")
	private LocalDateTime phoneHiddenAt;

	@Builder
	private Rental(Clothing clothing, User owner, User borrower, LocalDate startDate, LocalDate endDate,
			TradeMethod tradeMethod, int appliedRentalFee, Integer appliedDeliveryFee,
			LaundryBy appliedLaundryBy, LaundryMethod appliedLaundryMethod,
			LocalDateTime rulesAgreedAt, LocalDateTime requestedAt, LocalDateTime expiresAt) {
		this.clothing = clothing;
		this.owner = owner;
		this.borrower = borrower;
		this.startDate = startDate;
		this.endDate = endDate;
		this.tradeMethod = tradeMethod;
		this.appliedRentalFee = appliedRentalFee;
		this.appliedDeliveryFee = appliedDeliveryFee;
		this.appliedLaundryBy = appliedLaundryBy;
		this.appliedLaundryMethod = appliedLaundryMethod;
		this.rulesAgreedAt = rulesAgreedAt;
		this.requestedAt = requestedAt;
		this.expiresAt = expiresAt;
		this.status = RentalStatus.REQUESTED;
	}

	/**
	 * 대여 신청을 만든다. 신청 시점의 대여료·택배비·세탁 조건을 applied_* 로 복사해 두어,
	 * 이후 옷 주인이 조건을 바꿔도 이 신청에는 영향이 없게 한다.
	 */
	public static Rental request(Clothing clothing, User borrower, RentalPeriod period,
			TradeMethod tradeMethod, LocalDateTime now) {
		if (clothing.getStatus() != ClothingStatus.AVAILABLE || clothing.getDeletedAt() != null) {
			throw new RentalException(RentalErrorCode.CLOTHING_NOT_RENTABLE);
		}
		if (Objects.equals(clothing.getOwner().getId(), borrower.getId())) {
			throw new RentalException(RentalErrorCode.CANNOT_RENT_OWN_CLOTHING);
		}
		if (tradeMethod == TradeMethod.DELIVERY && !clothing.isDeliveryAvailable()) {
			throw new RentalException(RentalErrorCode.DELIVERY_NOT_AVAILABLE);
		}
		return Rental.builder()
				.clothing(clothing)
				.owner(clothing.getOwner())
				.borrower(borrower)
				.startDate(period.startDate())
				.endDate(period.endDate())
				.tradeMethod(tradeMethod)
				.appliedRentalFee(clothing.getRentalFee())
				.appliedDeliveryFee(tradeMethod == TradeMethod.DELIVERY ? clothing.getDeliveryFee() : null)
				.appliedLaundryBy(clothing.getLaundryBy())
				.appliedLaundryMethod(clothing.getLaundryMethod())
				.rulesAgreedAt(now)
				.requestedAt(now)
				.expiresAt(now.plus(RESPONSE_DEADLINE))
				.build();
	}

	public RentalPeriod getPeriod() {
		return new RentalPeriod(startDate, endDate);
	}

	public boolean isOwnedBy(Long userId) {
		return Objects.equals(owner.getId(), userId);
	}

	public boolean isBorrowedBy(Long userId) {
		return Objects.equals(borrower.getId(), userId);
	}

	public boolean isExpiredAt(LocalDateTime now) {
		return now.isAfter(expiresAt);
	}

	/** 신청이 처음 만들어졌을 때의 이력. */
	public RentalStatusLog createdLog() {
		return RentalStatusLog.builder()
				.rental(this)
				.fromStatus(null)
				.toStatus(RentalStatus.REQUESTED)
				.actorType(ActorType.BORROWER)
				.actor(borrower)
				.build();
	}

	/** 옷 주인이 수락한다. 48시간이 지난 신청은 자동 만료 배치가 없어도 수락할 수 없다. */
	public RentalStatusLog accept(LocalDateTime now) {
		requireStatus(RentalStatus.REQUESTED);
		if (isExpiredAt(now)) {
			throw new RentalException(RentalErrorCode.RENTAL_REQUEST_EXPIRED);
		}
		this.acceptedAt = now;
		return changeStatus(RentalStatus.ACCEPTED, ActorType.OWNER, owner, null);
	}

	/** 옷 주인이 사유를 골라 거절한다. 기타(ETC)면 직접 입력이 필요하다. */
	public RentalStatusLog reject(RejectReason reason, String detail) {
		requireStatus(RentalStatus.REQUESTED);
		if (reason == null) {
			throw new RentalException(RentalErrorCode.REJECT_REASON_REQUIRED);
		}
		if (reason == RejectReason.ETC && (detail == null || detail.isBlank())) {
			throw new RentalException(RentalErrorCode.REJECT_REASON_DETAIL_REQUIRED);
		}
		this.rejectReason = reason;
		this.rejectReasonDetail = reason == RejectReason.ETC ? detail.strip() : null;
		return changeStatus(RentalStatus.REJECTED, ActorType.OWNER, owner, null);
	}

	/** 수락 전 대여자가 신청을 취소한다. 사유는 받지 않는다. (수락 후 취소는 UT 이후) */
	public RentalStatusLog cancelByBorrower() {
		requireStatus(RentalStatus.REQUESTED);
		this.canceledBy = ActorType.BORROWER;
		return changeStatus(RentalStatus.CANCELED, ActorType.BORROWER, borrower, null);
	}

	/** 기간이 겹치는 다른 신청이 수락되어 이 신청이 무효가 된다. */
	public RentalStatusLog invalidate() {
		requireStatus(RentalStatus.REQUESTED);
		return changeStatus(RentalStatus.INVALIDATED, ActorType.SYSTEM, null, "기간이 겹치는 다른 신청이 수락됨");
	}

	private void requireStatus(RentalStatus expected) {
		if (status != expected) {
			throw new RentalException(RentalErrorCode.INVALID_RENTAL_STATUS);
		}
	}

	private RentalStatusLog changeStatus(RentalStatus to, ActorType actorType, User actor, String reason) {
		RentalStatus from = this.status;
		this.status = to;
		return RentalStatusLog.builder()
				.rental(this)
				.fromStatus(from)
				.toStatus(to)
				.actorType(actorType)
				.actor(actor)
				.reason(reason)
				.build();
	}

}
