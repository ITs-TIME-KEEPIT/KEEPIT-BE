package com.keepit.clothing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingPhotoRequest;
import com.keepit.clothing.dto.MeasurementRequest;
import com.keepit.clothing.dto.UnavailablePeriodRequest;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.global.config.ClockConfig;
import com.keepit.global.config.JpaConfig;
import com.keepit.image.entity.Image;
import com.keepit.region.entity.Region;
import com.keepit.user.entity.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 옷 등록이 실제 MySQL(Testcontainers, Flyway V1~V3)에서 이미지 연결·저장까지 의도대로 동작하는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class, ClothingService.class,
		ClothingServiceTest.FixedClockConfig.class})
@ActiveProfiles("test")
class ClothingServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 14, 0);
	private static final int MAPO = 14;
	private static final BigDecimal LEN = new BigDecimal("65.5");

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {
		@Bean
		Clock clock() {
			return Clock.fixed(NOW.atZone(ClockConfig.SEOUL).toInstant(), ClockConfig.SEOUL);
		}
	}

	@Autowired
	private TestEntityManager em;
	@Autowired
	private ClothingService clothingService;

	private User owner;
	private Image front;
	private Image back;
	private Image label;
	private Image defect;

	@BeforeEach
	void setUp() {
		owner = em.persist(User.builder().kakaoId("k-owner").nickname("등록자").build());
		front = em.persist(image(owner, "clothes/front.jpg", LocalDateTime.of(2026, 11, 19, 13, 50)));
		back = em.persist(image(owner, "clothes/back.jpg", null));
		label = em.persist(image(owner, "clothes/label.jpg", null));
		defect = em.persist(image(owner, "clothes/defect.jpg", null));
		em.flush();
	}

	private static Image image(User uploader, String key, LocalDateTime takenAt) {
		return Image.builder()
				.uploader(uploader)
				.imageKey(key)
				.serverReceivedAt(LocalDateTime.of(2026, 11, 19, 13, 55))
				.deviceTakenAt(takenAt)
				.build();
	}

	private ClothingCreateRequest request(ClothingCategory category, ClothingSize size, MeasurementRequest measurement,
			boolean hasDefect, String defectDescription, boolean deliveryAvailable, Integer deliveryFee,
			LaundryMethod laundryMethod, int regionId, List<ClothingPhotoRequest> photos) {
		return new ClothingCreateRequest("트위드 자켓", "한 번 입었어요", category, size, List.of(Color.NAVY, Color.BEIGE),
				measurement, hasDefect, defectDescription, regionId, 15000, deliveryAvailable, deliveryFee,
				LaundryBy.BORROWER, laundryMethod,
				List.of(new UnavailablePeriodRequest(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26))),
				photos);
	}

	private List<ClothingPhotoRequest> photosWithDefect() {
		return List.of(
				new ClothingPhotoRequest(front.getId(), PhotoPart.FRONT, 0),
				new ClothingPhotoRequest(back.getId(), PhotoPart.BACK, 1),
				new ClothingPhotoRequest(label.getId(), PhotoPart.LABEL, 2),
				new ClothingPhotoRequest(defect.getId(), PhotoPart.DEFECT, 3));
	}

	private List<ClothingPhoto> photosOf(Long clothingId) {
		return em.getEntityManager()
				.createQuery("select p from ClothingPhoto p where p.clothing.id = :id order by p.sortOrder, p.id",
						ClothingPhoto.class)
				.setParameter("id", clothingId)
				.getResultList();
	}

	@Test
	@DisplayName("옷과 컬러·대여 불가 기간·사진을 저장하고, 이미지를 연결하며 key와 시각을 사진으로 복사한다")
	void 등록_성공() {
		MeasurementRequest measurement = new MeasurementRequest(LEN, new BigDecimal("40.0"), null, null,
				new BigDecimal("30.0"), null, null, null);

		Long clothingId = clothingService.register(owner.getId(), request(ClothingCategory.OUTER, ClothingSize.M,
				measurement, true, "소매 올 풀림", true, 3500, LaundryMethod.DISCUSS, MAPO, photosWithDefect()));
		em.flush();
		em.clear();

		Clothing clothing = em.find(Clothing.class, clothingId);
		assertThat(clothing.getOwner().getId()).isEqualTo(owner.getId());
		assertThat(clothing.getRegion().getId()).isEqualTo(MAPO);
		assertThat(clothing.getCategory()).isEqualTo(ClothingCategory.OUTER);
		assertThat(clothing.getSize()).isEqualTo(ClothingSize.M);
		assertThat(clothing.getTotalLength()).isEqualByComparingTo(LEN);
		assertThat(clothing.getShoulderWidth()).isEqualByComparingTo("40.0");
		assertThat(clothing.getWaistWidth()).as("아우터에 없는 실측은 비운다").isNull();
		assertThat(clothing.isHasDefect()).isTrue();
		assertThat(clothing.getDefectDescription()).isEqualTo("소매 올 풀림");
		assertThat(clothing.getDeliveryFee()).isEqualTo(3500);
		assertThat(clothing.getLaundryMethod()).isEqualTo(LaundryMethod.DISCUSS);
		assertThat(clothing.getStatus()).isEqualTo(ClothingStatus.AVAILABLE);

		assertThat(em.getEntityManager()
				.createQuery("select c from ClothingColor c where c.clothing.id = :id", ClothingColor.class)
				.setParameter("id", clothingId).getResultList())
				.extracting(ClothingColor::getColor)
				.containsExactlyInAnyOrder(Color.NAVY, Color.BEIGE);
		assertThat(em.getEntityManager()
				.createQuery("select p from ClothingUnavailablePeriod p where p.clothing.id = :id",
						ClothingUnavailablePeriod.class)
				.setParameter("id", clothingId).getResultList())
				.singleElement()
				.satisfies(period -> {
					assertThat(period.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 24));
					assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 26));
				});

		List<ClothingPhoto> photos = photosOf(clothingId);
		assertThat(photos).extracting(ClothingPhoto::getPhotoPart)
				.containsExactly(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL, PhotoPart.DEFECT);
		assertThat(photos.get(0).getImageKey()).isEqualTo("clothes/front.jpg");
		assertThat(photos.get(0).getServerReceivedAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 13, 55));
		assertThat(photos.get(0).getDeviceTakenAt()).isEqualTo(LocalDateTime.of(2026, 11, 19, 13, 50));

		Image linkedFront = em.find(Image.class, front.getId());
		assertThat(linkedFront.getLinkedAt()).isEqualTo(NOW);
		assertThat(em.find(Image.class, defect.getId()).isLinked()).isTrue();
	}

	@Test
	@DisplayName("하자 없음·택배 불가·세탁 방법 미입력이면 하자 사진은 연결하지 않고 설명·택배비는 NULL, 세탁 방법은 DRY_CLEAN")
	void 기본값과_무시되는_값() {
		Long clothingId = clothingService.register(owner.getId(), request(ClothingCategory.TOP, ClothingSize.S,
				new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, "보내도 무시",
				false, 3000, null, MAPO, photosWithDefect()));
		em.flush();
		em.clear();

		Clothing clothing = em.find(Clothing.class, clothingId);
		assertThat(clothing.getDefectDescription()).isNull();
		assertThat(clothing.getDeliveryFee()).isNull();
		assertThat(clothing.getLaundryMethod()).isEqualTo(LaundryMethod.DRY_CLEAN);
		assertThat(photosOf(clothingId)).hasSize(3).noneMatch(photo -> photo.getPhotoPart() == PhotoPart.DEFECT);
		assertThat(em.find(Image.class, defect.getId()).isLinked()).isFalse();
	}

	@Test
	@DisplayName("잡화는 사이즈 FREE, 실측은 모두 NULL로 저장한다")
	void 잡화_저장() {
		MeasurementRequest ignored = new MeasurementRequest(LEN, LEN, LEN, LEN, LEN, LEN, LEN, LEN);

		Long clothingId = clothingService.register(owner.getId(), request(ClothingCategory.ACC, ClothingSize.L,
				ignored, false, null, false, null, null, MAPO, photosWithDefect()));
		em.flush();
		em.clear();

		Clothing clothing = em.find(Clothing.class, clothingId);
		assertThat(clothing.getSize()).isEqualTo(ClothingSize.FREE);
		assertThat(clothing.getTotalLength()).isNull();
		assertThat(clothing.getHemWidth()).isNull();
	}

	@Test
	@DisplayName("다른 사람이 올린 이미지가 섞이면 CLOTHING_400_11")
	void 남의_이미지() {
		User other = em.persist(User.builder().kakaoId("k-other").nickname("다른사람").build());
		Image othersFront = em.persist(image(other, "clothes/others.jpg", null));
		em.flush();
		List<ClothingPhotoRequest> photos = List.of(
				new ClothingPhotoRequest(othersFront.getId(), PhotoPart.FRONT, 0),
				new ClothingPhotoRequest(back.getId(), PhotoPart.BACK, 1),
				new ClothingPhotoRequest(label.getId(), PhotoPart.LABEL, 2));

		assertClothingError(() -> clothingService.register(owner.getId(), request(ClothingCategory.TOP,
				ClothingSize.M, new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null,
				false, null, null, MAPO, photos)), ClothingErrorCode.UNUSABLE_IMAGE);
	}

	@Test
	@DisplayName("이미 다른 옷에 연결된 이미지를 다시 쓰면 CLOTHING_400_11")
	void 이미_연결된_이미지() {
		clothingService.register(owner.getId(), request(ClothingCategory.TOP, ClothingSize.M,
				new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null, false, null,
				null, MAPO, photosWithDefect()));

		assertClothingError(() -> clothingService.register(owner.getId(), request(ClothingCategory.TOP,
				ClothingSize.M, new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null,
				false, null, null, MAPO, photosWithDefect())), ClothingErrorCode.UNUSABLE_IMAGE);
	}

	@Test
	@DisplayName("없는 지역이나 닫힌 지역이면 CLOTHING_400_12")
	void 선택할_수_없는_지역() {
		assertClothingError(() -> clothingService.register(owner.getId(), request(ClothingCategory.TOP,
				ClothingSize.M, new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null,
				false, null, null, 9999, photosWithDefect())), ClothingErrorCode.REGION_NOT_SELECTABLE);
	}

	@Test
	@DisplayName("닫힌 지역(is_open = FALSE)은 고를 수 없다")
	void 닫힌_지역() {
		Region closed = em.persist(Region.builder().sido("테스트").sigungu("닫힌구").open(false).build());
		em.flush();

		assertClothingError(() -> clothingService.register(owner.getId(), request(ClothingCategory.TOP,
				ClothingSize.M, new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null,
				false, null, null, closed.getId(), photosWithDefect())), ClothingErrorCode.REGION_NOT_SELECTABLE);
	}

	@Test
	@DisplayName("필드 간 규칙 위반은 DB에 손대기 전에 거절한다")
	void 검증_실패는_연결_전에_거절() {
		assertClothingError(() -> clothingService.register(owner.getId(), request(ClothingCategory.TOP,
				null, new MeasurementRequest(LEN, null, null, null, null, null, null, null), false, null,
				false, null, null, MAPO, photosWithDefect())), ClothingErrorCode.SIZE_REQUIRED);
		em.clear();

		assertThat(em.find(Image.class, front.getId()).isLinked()).isFalse();
	}

	private static void assertClothingError(Runnable action, ClothingErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(ClothingException.class)
				.extracting("responseCode").isEqualTo(expected);
	}

}
