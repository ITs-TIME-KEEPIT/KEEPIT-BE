package com.keepit.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.TestcontainersConfiguration;
import com.keepit.global.config.JpaConfig;
import com.keepit.image.entity.Image;
import com.keepit.user.entity.User;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Flyway V2 images 테이블 위에서 Image 매핑과 Repository 동작을 실제 MySQL(Testcontainers)로 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class ImageRepositoryTest {

	private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 11, 14, 15, 42, 10);

	@Autowired
	private TestEntityManager em;
	@Autowired
	private ImageRepository imageRepository;

	private User uploader;

	@BeforeEach
	void setUp() {
		uploader = em.persist(User.builder().kakaoId("k-uploader").nickname("업로더").build());
	}

	private Image image(User uploader, String key) {
		return Image.builder()
				.uploader(uploader)
				.imageKey(key)
				.serverReceivedAt(RECEIVED_AT)
				.build();
	}

	@Test
	@DisplayName("이미지를 저장하면 미연결 상태로 조회되고 생성·수정 시각이 기록된다")
	void 이미지_저장_조회() {
		LocalDateTime takenAt = LocalDateTime.of(2026, 11, 14, 15, 40);
		Image saved = imageRepository.save(Image.builder()
				.uploader(uploader)
				.imageKey("clothes/a.jpg")
				.serverReceivedAt(RECEIVED_AT)
				.deviceTakenAt(takenAt)
				.build());
		em.flush();
		em.clear();

		Image found = imageRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getUploader().getId()).isEqualTo(uploader.getId());
		assertThat(found.getImageKey()).isEqualTo("clothes/a.jpg");
		assertThat(found.getServerReceivedAt()).isEqualTo(RECEIVED_AT);
		assertThat(found.getDeviceTakenAt()).isEqualTo(takenAt);
		assertThat(found.isLinked()).isFalse();
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getUpdatedAt()).isNotNull();
	}

	@Test
	@DisplayName("같은 image_key는 두 번 저장할 수 없다")
	void 이미지_키_중복_거절() {
		em.persist(image(uploader, "clothes/dup.jpg"));
		em.flush();

		assertThatThrownBy(() -> {
			em.persist(image(uploader, "clothes/dup.jpg"));
			em.flush();
		}).isInstanceOf(PersistenceException.class);
	}

}
