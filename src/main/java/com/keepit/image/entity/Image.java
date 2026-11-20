package com.keepit.image.entity;

import com.keepit.global.entity.BaseTimeEntity;
import com.keepit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 업로드된 이미지. 업로드 시 미연결(linkedAt null)로 저장되고, 옷 등록 시 연결된다.
 * imageKey는 스토리지 객체 키이고, 응답에는 키 대신 업로더가 만든 URL을 쓴다.
 */
@Getter
@Entity
@Table(name = "images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Image extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploader_id", nullable = false)
	private User uploader;

	@Column(name = "image_key", nullable = false)
	private String imageKey;

	@Column(name = "server_received_at", nullable = false)
	private LocalDateTime serverReceivedAt;

	@Column(name = "device_taken_at")
	private LocalDateTime deviceTakenAt;

	@Column(name = "linked_at")
	private LocalDateTime linkedAt;

	@Builder
	private Image(User uploader, String imageKey, LocalDateTime serverReceivedAt, LocalDateTime deviceTakenAt) {
		this.uploader = uploader;
		this.imageKey = imageKey;
		this.serverReceivedAt = serverReceivedAt;
		this.deviceTakenAt = deviceTakenAt;
	}

	public boolean isLinked() {
		return linkedAt != null;
	}

}
