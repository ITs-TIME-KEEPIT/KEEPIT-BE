package com.keepit.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keepit.global.config.ClockConfig;
import com.keepit.global.s3.FileUploader;
import com.keepit.image.dto.ImageUploadResponse;
import com.keepit.image.entity.Image;
import com.keepit.image.exception.ImageErrorCode;
import com.keepit.image.exception.ImageException;
import com.keepit.image.repository.ImageRepository;
import com.keepit.image.support.TestImages;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.io.UncheckedIOException;
import java.io.IOException;
import java.util.Arrays;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ImageServiceTest {

	private static final Long UPLOADER_ID = 1L;
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 15, 42, 10);
	private static final byte[] JPEG = TestImages.encode("JPEG");
	private static final byte[] PNG = TestImages.encode("PNG");
	private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01};

	private final ImageRepository imageRepository = mock(ImageRepository.class);
	private final FileUploader fileUploader = mock(FileUploader.class);
	private final EntityManager entityManager = mock(EntityManager.class);
	private final Clock clock = Clock.fixed(NOW.atZone(ClockConfig.SEOUL).toInstant(), ClockConfig.SEOUL);

	private ImageService imageService;

	@BeforeEach
	void setUp() {
		imageService = new ImageService(imageRepository, new ImageFileStorage(fileUploader), entityManager, clock);
		when(entityManager.getReference(User.class, UPLOADER_ID)).thenReturn(mock(User.class));
		when(fileUploader.upload(any(), eq("clothes"))).thenReturn("clothes/abc.jpg");
		when(fileUploader.getUrl("clothes/abc.jpg")).thenReturn("/local-images/clothes/abc.jpg");
		when(imageRepository.save(any(Image.class))).thenAnswer(invocation -> {
			Image image = invocation.getArgument(0);
			ReflectionTestUtils.setField(image, "id", 10L);
			return image;
		});
	}

	private static MockMultipartFile file(String filename, byte[] content) {
		return new MockMultipartFile("file", filename, "application/octet-stream", content);
	}

	private Image savedImage() {
		ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
		verify(imageRepository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	@DisplayName("JPEG를 올리면 clothes 폴더에 저장하고 imageId와 URL을 돌려준다")
	void 업로드_성공() {
		ImageUploadResponse response = imageService.upload(UPLOADER_ID, file("photo.JPEG", JPEG), null);

		assertThat(response.imageId()).isEqualTo(10L);
		assertThat(response.imageUrl()).isEqualTo("/local-images/clothes/abc.jpg");
		Image saved = savedImage();
		assertThat(saved.getImageKey()).isEqualTo("clothes/abc.jpg");
		assertThat(saved.getServerReceivedAt()).isEqualTo(NOW);
		assertThat(saved.getDeviceTakenAt()).isNull();
		assertThat(saved.isLinked()).isFalse();
	}

	@Test
	@DisplayName("PNG도 올릴 수 있다")
	void PNG_업로드_성공() {
		imageService.upload(UPLOADER_ID, file("photo.png", PNG), null);

		verify(fileUploader).upload(any(), eq("clothes"));
	}

	@Test
	@DisplayName("기기 촬영 시각은 오프셋을 반영해 KST로 바꿔 저장한다")
	void 촬영_시각_KST_변환() {
		OffsetDateTime takenAtUtc = OffsetDateTime.of(2026, 11, 14, 6, 40, 0, 0, ZoneOffset.UTC);

		imageService.upload(UPLOADER_ID, file("photo.jpg", JPEG), takenAtUtc);

		assertThat(savedImage().getDeviceTakenAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 15, 40));
	}

	@Test
	@DisplayName("빈 파일은 IMAGE_400_1로 거절하고 저장하지 않는다")
	void 빈_파일_거절() {
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", new byte[0]), null),
				ImageErrorCode.IMAGE_FILE_REQUIRED);
	}

	@Test
	@DisplayName("허용하지 않는 형식은 IMAGE_400_2로 거절한다")
	void 미지원_형식_거절() {
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", GIF), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
	}

	@Test
	void truncatedImagesNeverReachStorage() {
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", Arrays.copyOf(JPEG, JPEG.length - 2)), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.png", Arrays.copyOf(PNG, PNG.length - 1)), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
	}

	@Test
	void matchingSignatureAndTrailerAreInsufficient() {
		byte[] invalid = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, (byte) 0xff, (byte) 0xd9};
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", invalid), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
	}

	@Test
	@DisplayName("파일명 확장자가 실제 형식과 다르면 IMAGE_400_2로 거절한다")
	void 확장자_불일치_거절() {
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", PNG), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
	}

	@Test
	@DisplayName("파일명에 확장자가 없으면 IMAGE_400_2로 거절한다")
	void 확장자_없음_거절() {
		assertImageError(() -> imageService.upload(UPLOADER_ID, file("photo", JPEG), null),
				ImageErrorCode.UNSUPPORTED_IMAGE_FORMAT);
	}

	@Test
	@DisplayName("저장소에서 실패하면 IMAGE_500_1로 바꾸고 DB에 기록하지 않는다")
	void 저장_실패() {
		when(fileUploader.upload(any(), eq("clothes")))
				.thenThrow(new UncheckedIOException("disk full", new IOException()));

		assertThatThrownBy(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", JPEG), null))
				.isInstanceOf(ImageException.class)
				.extracting("responseCode").isEqualTo(ImageErrorCode.IMAGE_UPLOAD_FAILED);
		verify(imageRepository, never()).save(any());
	}

	@Nested
	@DisplayName("트랜잭션 롤백 시 업로드 파일 정리")
	class 롤백_시_파일_정리 {

		@BeforeEach
		void startSynchronization() {
			// 실제로는 @Transactional이 켜 주는 트랜잭션 동기화를 단위 테스트에서 직접 켠다
			TransactionSynchronizationManager.initSynchronization();
		}

		@AfterEach
		void clearSynchronization() {
			TransactionSynchronizationManager.clearSynchronization();
		}

		private void completeTransaction(int status) {
			for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
				synchronization.afterCompletion(status);
			}
		}

		@Test
		@DisplayName("(a) DB 저장이 실패해 롤백되면 저장한 파일을 지운다")
		void DB_저장_실패_후_롤백이면_삭제() {
			when(imageRepository.save(any(Image.class))).thenThrow(new DataIntegrityViolationException("fk"));

			assertThatThrownBy(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", JPEG), null))
					.isInstanceOf(DataIntegrityViolationException.class);
			completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

			verify(fileUploader).delete("clothes/abc.jpg");
		}

		@Test
		@DisplayName("(b) 커밋되면 파일을 지우지 않는다")
		void 커밋이면_유지() {
			imageService.upload(UPLOADER_ID, file("photo.jpg", JPEG), null);
			completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

			verify(fileUploader, never()).delete(any());
		}

		@Test
		@DisplayName("(c) 파일 삭제에 실패해도 예외를 밖으로 던지지 않는다")
		void 삭제_실패는_로그만() {
			when(imageRepository.save(any(Image.class))).thenThrow(new DataIntegrityViolationException("fk"));
			doThrow(new UncheckedIOException("locked", new IOException())).when(fileUploader).delete(any());
			assertThatThrownBy(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", JPEG), null))
					.isInstanceOf(DataIntegrityViolationException.class);

			assertThatCode(() -> completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK))
					.doesNotThrowAnyException();
			verify(fileUploader).delete("clothes/abc.jpg");
		}

		@Test
		@DisplayName("파일을 저장하기 전에 검증에서 거절되면 정리할 것도 등록하지 않는다")
		void 검증_실패는_등록_없음() {
			assertThatThrownBy(() -> imageService.upload(UPLOADER_ID, file("photo.jpg", GIF), null))
					.isInstanceOf(ImageException.class);

			assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
		}
	}

	private void assertImageError(Runnable action, ImageErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(ImageException.class)
				.extracting("responseCode").isEqualTo(expected);
		verify(fileUploader, never()).upload(any(), any());
		verify(imageRepository, never()).save(any());
	}

}
