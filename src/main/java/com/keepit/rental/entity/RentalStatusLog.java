package com.keepit.rental.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대여 상태 전이 이력.
 * fromStatus는 최초 생성 로그에서 null, actor는 actorType이 SYSTEM이면 null.
 */
@Getter
@Entity
@Table(name = "rental_status_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalStatusLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rental_id", nullable = false)
	private Rental rental;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "from_status", length = 30)
	private RentalStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "to_status", nullable = false, length = 30)
	private RentalStatus toStatus;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "actor_type", nullable = false, length = 30)
	private ActorType actorType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id")
	private User actor;

	@Column
	private String reason;

	@Builder
	private RentalStatusLog(Rental rental, RentalStatus fromStatus, RentalStatus toStatus,
			ActorType actorType, User actor, String reason) {
		this.rental = rental;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.actorType = actorType;
		this.actor = actor;
		this.reason = reason;
	}

}
