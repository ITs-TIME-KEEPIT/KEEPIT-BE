package com.keepit.rental.entity;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.global.entity.BaseTimeEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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

}
