package com.keepit.rental.entity;

import com.keepit.clothing.entity.PhotoPart;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 인계·수령·반납 단계별 상태 확인 사진.
 */
@Getter
@Entity
@Table(name = "rental_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RentalPhoto extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rental_id", nullable = false)
	private Rental rental;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private RentalPhotoStage stage;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "photo_part", nullable = false, length = 30)
	private PhotoPart photoPart;

	@Column(name = "image_key", nullable = false)
	private String imageKey;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "server_received_at", nullable = false)
	private LocalDateTime serverReceivedAt;

	@Column(name = "device_taken_at")
	private LocalDateTime deviceTakenAt;

	@Builder
	private RentalPhoto(Rental rental, User uploader, RentalPhotoStage stage, PhotoPart photoPart,
			String imageKey, int sortOrder, LocalDateTime serverReceivedAt, LocalDateTime deviceTakenAt) {
		this.rental = rental;
		this.uploader = uploader;
		this.stage = stage;
		this.photoPart = photoPart;
		this.imageKey = imageKey;
		this.sortOrder = sortOrder;
		this.serverReceivedAt = serverReceivedAt;
		this.deviceTakenAt = deviceTakenAt;
	}

}
