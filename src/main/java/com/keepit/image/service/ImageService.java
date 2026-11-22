package com.keepit.image.service;

import com.keepit.global.config.ClockConfig;
import com.keepit.image.dto.ImageUploadResponse;
import com.keepit.image.entity.Image;
import com.keepit.image.repository.ImageRepository;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 옷 사진 업로드. 형식을 검증한 뒤 저장소에 올리고, 미연결 이미지로 기록한다(옷 등록 때 연결).
 */
@Service
@RequiredArgsConstructor
public class ImageService {

	static final String CLOTHES_DIRECTORY = "clothes";

	private final ImageRepository imageRepository;
	private final ImageFileStorage storage;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * @param deviceTakenAt 기기 촬영 시각(선택). 오프셋이 있는 값을 받아 KST로 바꿔 저장한다.
	 */
	@Transactional
	public ImageUploadResponse upload(Long uploaderId, MultipartFile file, OffsetDateTime deviceTakenAt) {
		LocalDateTime serverReceivedAt = LocalDateTime.now(clock);
		// 검증된 파일을 저장하고 옷 등록 때 연결할 이미지 행을 만든다.
		String key = storage.store(file, CLOTHES_DIRECTORY);
		Image image = imageRepository.save(Image.builder()
				.uploader(entityManager.getReference(User.class, uploaderId))
				.imageKey(key)
				.serverReceivedAt(serverReceivedAt)
				.deviceTakenAt(toKst(deviceTakenAt))
				.build());
		return new ImageUploadResponse(image.getId(), storage.getUrl(key));
	}

	private static LocalDateTime toKst(OffsetDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.atZoneSameInstant(ClockConfig.SEOUL).toLocalDateTime();
	}

}
