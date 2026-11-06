package com.keepit.user.entity;

import com.keepit.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 회원. 탈퇴 후 같은 카카오 계정으로 재가입하면 기존 row를 재사용한다 (kakao_id UNIQUE).
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "kakao_id", nullable = false, unique = true, length = 64)
	private String kakaoId;

	@Column(nullable = false, length = 20)
	private String nickname;

	@Column(name = "profile_image_key")
	private String profileImageKey;

	@Column(length = 20)
	private String phone;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private UserStatus status;

	@Column(name = "suspend_reason")
	private String suspendReason;

	@Column(name = "suspended_at")
	private LocalDateTime suspendedAt;

	@Column(name = "suspended_until")
	private LocalDateTime suspendedUntil;

	@Column(name = "delete_reason")
	private String deleteReason;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder
	private User(String kakaoId, String nickname, String profileImageKey, UserRole role) {
		this.kakaoId = kakaoId;
		this.nickname = nickname;
		this.profileImageKey = profileImageKey;
		this.role = role == null ? UserRole.USER : role;
		this.status = UserStatus.ACTIVE;
	}

}
