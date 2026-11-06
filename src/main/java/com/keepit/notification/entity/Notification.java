package com.keepit.notification.entity;

import com.keepit.global.entity.BaseTimeEntity;
import com.keepit.rental.entity.Rental;
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
 * 사용자 알림. 대여와 무관한 알림(ADMIN_STATUS_CHANGED 등)은 rental이 null.
 */
@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "rental_id")
	private Rental rental;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Builder
	private Notification(User user, Rental rental, NotificationType type) {
		this.user = user;
		this.rental = rental;
		this.type = type;
		this.read = false;
	}

	public void markAsRead() {
		this.read = true;
	}

}
