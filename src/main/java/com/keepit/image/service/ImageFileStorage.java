package com.keepit.image.service;

import com.keepit.global.s3.FileUploader;
import com.keepit.image.exception.ImageErrorCode;
import com.keepit.image.exception.ImageException;
import com.keepit.image.support.ImageFormat;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageFileStorage {

	private final FileUploader uploader;

	public String store(MultipartFile file, String directory) {
		// 옷과 프로필 사진은 업로드 전에 확장자와 실제 이미지 데이터를 검사한다.
		if (file == null || file.isEmpty()) {
			throw new ImageException(ImageErrorCode.IMAGE_FILE_REQUIRED);
		}
		try {
			byte[] content = file.getBytes();
			ImageFormat format = ImageFormat.detect(content)
					.orElseThrow(() -> new ImageException(ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT));
			if (!format.matchesExtension(file.getOriginalFilename()) || !format.canDecode(content)) {
				throw new ImageException(ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
			}
		} catch (IOException exception) {
			throw new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED, exception);
		}

		// 저장소 구현의 실패를 공통 이미지 오류로 변환한다.
		String key;
		try {
			key = uploader.upload(file, directory);
		} catch (RuntimeException exception) {
			throw new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED, exception);
		}

		// 파일 저장 뒤 DB 커밋이 실패하면 해당 요청이 올린 파일만 정리한다.
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status == STATUS_ROLLED_BACK) {
						try {
							uploader.delete(key);
						} catch (RuntimeException exception) {
							log.warn("롤백된 업로드 파일 삭제 실패: key={}", key, exception);
						}
					}
				}
			});
		}
		return key;
	}

	public String getUrl(String key) {
		return uploader.getUrl(key);
	}

}
