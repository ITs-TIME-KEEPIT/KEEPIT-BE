package com.keepit.clothing.entity;

import com.keepit.global.entity.BaseTimeEntity;
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
 * 의류 등록 사진. image_key는 스토리지(S3) 객체 키.
 */
@Getter
@Entity
@Table(name = "clothing_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothingPhoto extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "clothing_id", nullable = false)
	private Clothing clothing;

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
	private ClothingPhoto(Clothing clothing, PhotoPart photoPart, String imageKey, int sortOrder,
			LocalDateTime serverReceivedAt, LocalDateTime deviceTakenAt) {
		this.clothing = clothing;
		this.photoPart = photoPart;
		this.imageKey = imageKey;
		this.sortOrder = sortOrder;
		this.serverReceivedAt = serverReceivedAt;
		this.deviceTakenAt = deviceTakenAt;
	}

}
