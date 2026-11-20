package com.keepit.image.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.TestcontainersConfiguration;
import com.keepit.global.config.JpaConfig;
import com.keepit.image.entity.Image;
import com.keepit.user.entity.User;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
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
	@DisplayName("본인의 미연결 이미지는 연결되고 linked_at·updated_at이 요청 시각으로 바뀐다")
	void 조건부_연결_성공() {
		Image first = em.persist(image(uploader, "clothes/1.jpg"));
		Image second = em.persist(image(uploader, "clothes/2.jpg"));
		em.flush();
		LocalDateTime linkedAt = LocalDateTime.of(2026, 11, 14, 16, 0);

		int updated = imageRepository.link(List.of(first.getId(), second.getId()), uploader.getId(), linkedAt);

		assertThat(updated).isEqualTo(2);
		Image found = imageRepository.findById(first.getId()).orElseThrow();
		assertThat(found.isLinked()).isTrue();
		assertThat(found.getLinkedAt()).isEqualTo(linkedAt);
		assertThat(found.getUpdatedAt()).isEqualTo(linkedAt);
	}

	@Test
	@DisplayName("이미 연결된 이미지는 다시 연결되지 않아 반영 행 수가 요청 수보다 적다")
	void 이미_연결된_이미지_제외() {
		Image linked = em.persist(image(uploader, "clothes/linked.jpg"));
		Image fresh = em.persist(image(uploader, "clothes/fresh.jpg"));
		em.flush();
		LocalDateTime firstLink = LocalDateTime.of(2026, 11, 14, 16, 0);
		imageRepository.link(List.of(linked.getId()), uploader.getId(), firstLink);

		int updated = imageRepository.link(List.of(linked.getId(), fresh.getId()), uploader.getId(),
				firstLink.plusHours(1));

		assertThat(updated).isEqualTo(1);
		assertThat(imageRepository.findById(linked.getId()).orElseThrow().getLinkedAt()).isEqualTo(firstLink);
	}

	@Test
	@DisplayName("다른 사람이 올린 이미지는 연결되지 않는다")
	void 다른_업로더_이미지_제외() {
		User other = em.persist(User.builder().kakaoId("k-other").nickname("다른사람").build());
		Image othersImage = em.persist(image(other, "clothes/others.jpg"));
		em.flush();

		int updated = imageRepository.link(List.of(othersImage.getId()), uploader.getId(),
				LocalDateTime.of(2026, 11, 14, 16, 0));

		assertThat(updated).isZero();
		assertThat(imageRepository.findById(othersImage.getId()).orElseThrow().isLinked()).isFalse();
	}

	@Test
	@DisplayName("없는 id가 섞이면 반영 행 수가 요청 수보다 적다")
	void 없는_이미지_id_포함() {
		Image mine = em.persist(image(uploader, "clothes/mine.jpg"));
		em.flush();

		int updated = imageRepository.link(List.of(mine.getId(), Long.MAX_VALUE), uploader.getId(),
				LocalDateTime.of(2026, 11, 14, 16, 0));

		assertThat(updated).isEqualTo(1);
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
