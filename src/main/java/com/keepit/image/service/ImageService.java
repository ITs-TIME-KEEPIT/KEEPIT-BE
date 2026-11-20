package com.keepit.image.service;

import com.keepit.global.config.ClockConfig;
import com.keepit.global.s3.FileUploader;
import com.keepit.image.dto.ImageUploadResponse;
import com.keepit.image.entity.Image;
import com.keepit.image.exception.ImageErrorCode;
import com.keepit.image.exception.ImageException;
import com.keepit.image.repository.ImageRepository;
import com.keepit.image.support.ImageFormat;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 옷 사진 업로드. 형식을 검증한 뒤 저장소에 올리고, 미연결 이미지로 기록한다(옷 등록 때 연결).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

	static final String CLOTHES_DIRECTORY = "clothes";

	private final ImageRepository imageRepository;
	private final FileUploader fileUploader;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * @param deviceTakenAt 기기 촬영 시각(선택). 오프셋이 있는 값을 받아 KST로 바꿔 저장한다.
	 */
	@Transactional
	public ImageUploadResponse upload(Long uploaderId, MultipartFile file, OffsetDateTime deviceTakenAt) {
		LocalDateTime serverReceivedAt = LocalDateTime.now(clock);
		if (file == null || file.isEmpty()) {
			throw new ImageException(ImageErrorCode.IMAGE_FILE_REQUIRED);
		}
		ImageFormat format = detectFormat(file);
		if (!format.matchesExtension(file.getOriginalFilename())) {
			throw new ImageException(ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
		}

		String key = store(file);
		deleteFileOnRollback(key);
		Image image = imageRepository.save(Image.builder()
				.uploader(entityManager.getReference(User.class, uploaderId))
				.imageKey(key)
				.serverReceivedAt(serverReceivedAt)
				.deviceTakenAt(toKst(deviceTakenAt))
				.build());
		return new ImageUploadResponse(image.getId(), fileUploader.getUrl(key));
	}

	/**
	 * 트랜잭션이 롤백으로 끝나면(DB 저장 실패 등) 이미 저장한 파일을 지운다. 커밋된 요청의 파일은 그대로 둔다.
	 * 삭제에 실패해도 원래 오류 응답을 가리지 않도록 로그만 남긴다(로그에는 key만 남긴다).
	 */
	private void deleteFileOnRollback(String key) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status != STATUS_ROLLED_BACK) {
					return;
				}
				try {
					fileUploader.delete(key);
				} catch (RuntimeException e) {
					log.warn("롤백된 업로드 파일 삭제 실패: key={}", key, e);
				}
			}
		});
	}

	/** 파일 전체가 아니라 판별에 필요한 앞부분만 읽는다. */
	private ImageFormat detectFormat(MultipartFile file) {
		try (InputStream inputStream = file.getInputStream()) {
			return ImageFormat.detect(inputStream)
					.orElseThrow(() -> new ImageException(ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT));
		} catch (IOException e) {
			throw new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED, e);
		}
	}

	/** 저장소 구현(로컬·S3)이 던지는 예외는 모두 RuntimeException이라 넓게 잡아 공통 응답 형식으로 바꾼다. */
	private String store(MultipartFile file) {
		try {
			return fileUploader.upload(file, CLOTHES_DIRECTORY);
		} catch (RuntimeException e) {
			throw new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED, e);
		}
	}

	private static LocalDateTime toKst(OffsetDateTime dateTime) {
		if (dateTime == null) {
			return null;
		}
		return dateTime.atZoneSameInstant(ClockConfig.SEOUL).toLocalDateTime();
	}

}
