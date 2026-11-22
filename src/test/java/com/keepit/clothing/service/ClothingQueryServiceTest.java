package com.keepit.clothing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.dto.ClothingCardResponse;
import com.keepit.clothing.dto.ClothingDetailResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.dto.RegionResponse;
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
import com.keepit.global.config.JpaConfig;
import com.keepit.global.s3.LocalFileUploader;
import com.keepit.region.entity.Region;
import com.keepit.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 옷 목록·상세 조회를 실제 MySQL(Testcontainers, Flyway V1~V3)에서 확인한다.
 * 이미지 URL은 test 프로필의 LocalFileUploader가 만든다(/local-images/{key}).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class, ClothingQueryService.class, LocalFileUploader.class})
@ActiveProfiles("test")
class ClothingQueryServiceTest {

	private static final int MAPO = 14;
	private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 19, 10, 0);

	@Autowired
	private TestEntityManager em;
	@Autowired
	private ClothingQueryService queryService;

	private User owner;
	private Region mapo;

	@BeforeEach
	void setUp() {
		owner = em.persist(User.builder().kakaoId("k-owner").nickname("옷장주인").build());
		mapo = em.find(Region.class, MAPO);
	}

	/** 옷을 저장하고 created_at을 BASE + minutes로 맞춘다(정렬 확인용). */
	private Clothing clothing(String title, ClothingCategory category, int minutes) {
		Clothing clothing = em.persist(Clothing.builder()
				.owner(owner).region(mapo).title(title)
				.category(category).size(ClothingSize.M).totalLength(new BigDecimal("60.0"))
				.rentalFee(10000).deliveryAvailable(true).deliveryFee(3000)
				.laundryBy(LaundryBy.OWNER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build());
		em.flush();
		nativeUpdate("update clothes set created_at = ? where id = ?", BASE.plusMinutes(minutes), clothing.getId());
		return clothing;
	}

	private void photo(Clothing clothing, PhotoPart part, int sortOrder, String key) {
		em.persist(ClothingPhoto.builder().clothing(clothing).photoPart(part).imageKey(key)
				.sortOrder(sortOrder).serverReceivedAt(BASE).build());
	}

	private void nativeUpdate(String sql, Object... params) {
		var query = em.getEntityManager().createNativeQuery(sql);
		for (int i = 0; i < params.length; i++) {
			query.setParameter(i + 1, params[i]);
		}
		query.executeUpdate();
	}

	private void flushAndClear() {
		em.flush();
		em.clear();
	}

	@Test
	@DisplayName("대여 가능·미삭제 옷만 최신 등록순(id 내림차순)으로 주고, 카드 필드를 채운다")
	void 목록_필터와_정렬_카드필드() {
		Clothing older = clothing("오래된 셔츠", ClothingCategory.TOP, 0);
		Clothing newer = clothing("새 원피스", ClothingCategory.DRESS, 10);
		Clothing paused = clothing("대여 중지 옷", ClothingCategory.TOP, 20);
		Clothing deleted = clothing("삭제된 옷", ClothingCategory.TOP, 30);
		nativeUpdate("update clothes set status = 'PAUSED' where id = ?", paused.getId());
		nativeUpdate("update clothes set deleted_at = ? where id = ?", BASE, deleted.getId());
		photo(newer, PhotoPart.FRONT, 1, "clothes/front-1.jpg");
		photo(newer, PhotoPart.FRONT, 0, "clothes/front-0.jpg");
		photo(newer, PhotoPart.BACK, -1, "clothes/back.jpg");
		flushAndClear();

		ClothingListResponse response = queryService.getList(null, null, 20);

		assertThat(response.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(newer.getId(), older.getId());
		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
		ClothingCardResponse card = response.content().get(0);
		assertThat(card.thumbnailUrl()).as("전면 사진 중 sort_order가 가장 작은 것")
				.isEqualTo("/local-images/clothes/front-0.jpg");
		assertThat(card.title()).isEqualTo("새 원피스");
		assertThat(card.category()).isEqualTo(ClothingCategory.DRESS);
		assertThat(card.size()).isEqualTo(ClothingSize.M);
		assertThat(card.region()).isEqualTo(new RegionResponse(MAPO, "서울", "마포구"));
		assertThat(card.rentalFee()).isEqualTo(10000);
		assertThat(card.laundryBy()).isEqualTo(LaundryBy.OWNER);
		assertThat(card.deliveryAvailable()).isTrue();
		assertThat(card.createdAt()).isEqualTo(BASE.plusMinutes(10));
		assertThat(response.content().get(1).thumbnailUrl()).as("전면 사진이 없으면 null").isNull();
	}

	@Test
	@DisplayName("카테고리를 주면 그 카테고리만 준다")
	void 카테고리_필터() {
		clothing("셔츠", ClothingCategory.TOP, 0);
		Clothing dress = clothing("원피스", ClothingCategory.DRESS, 1);
		flushAndClear();

		assertThat(queryService.getList(ClothingCategory.DRESS, null, 20).content())
				.extracting(ClothingCardResponse::clothesId)
				.containsExactly(dress.getId());
	}

	@Test
	@DisplayName("nextCursor로 이어 받으면 겹치거나 빠지는 옷 없이 끝까지 받고, 마지막 묶음은 nextCursor가 null")
	void 커서로_이어_받기() {
		Clothing first = clothing("1", ClothingCategory.TOP, 0);
		Clothing second = clothing("2", ClothingCategory.TOP, 1);
		Clothing third = clothing("3", ClothingCategory.TOP, 2);
		flushAndClear();

		ClothingListResponse firstBatch = queryService.getList(null, null, 2);
		ClothingListResponse secondBatch = queryService.getList(null, firstBatch.nextCursor(), 2);

		assertThat(firstBatch.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(third.getId(), second.getId());
		assertThat(firstBatch.hasNext()).isTrue();
		assertThat(firstBatch.nextCursor()).as("다음 커서는 이번 묶음의 마지막 id").isEqualTo(second.getId());
		assertThat(secondBatch.content()).extracting(ClothingCardResponse::clothesId).containsExactly(first.getId());
		assertThat(secondBatch.hasNext()).isFalse();
		assertThat(secondBatch.nextCursor()).isNull();
	}

	@Test
	@DisplayName("스크롤 중에 새 옷이 등록돼도 다음 묶음에 같은 옷이 다시 나오지 않는다")
	void 스크롤_중_새_등록() {
		Clothing first = clothing("1", ClothingCategory.TOP, 0);
		Clothing second = clothing("2", ClothingCategory.TOP, 1);
		Clothing third = clothing("3", ClothingCategory.TOP, 2);
		flushAndClear();
		ClothingListResponse firstBatch = queryService.getList(null, null, 2);

		clothing("새로 등록", ClothingCategory.TOP, 3);
		flushAndClear();
		ClothingListResponse secondBatch = queryService.getList(null, firstBatch.nextCursor(), 2);

		assertThat(firstBatch.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(third.getId(), second.getId());
		assertThat(secondBatch.content()).extracting(ClothingCardResponse::clothesId).containsExactly(first.getId());
	}

	@Test
	@DisplayName("없는 id를 커서로 보내도 그보다 작은 id의 옷을 정상으로 준다")
	void 없는_id_커서() {
		Clothing first = clothing("1", ClothingCategory.TOP, 0);
		Clothing second = clothing("2", ClothingCategory.TOP, 1);
		flushAndClear();

		ClothingListResponse response = queryService.getList(null, second.getId() + 1000, 20);

		assertThat(response.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(second.getId(), first.getId());
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	@DisplayName("조건에 맞는 옷이 없으면 빈 목록")
	void 빈_목록() {
		ClothingListResponse response = queryService.getList(ClothingCategory.ACC, null, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.hasNext()).isFalse();
		assertThat(response.nextCursor()).isNull();
	}

	@Test
	@DisplayName("상세는 옷 정보·실측·컬러·원본 대여 불가 기간·사진·등록자·등록 시각을 준다")
	void 상세_전체_필드() {
		User profileOwner = em.persist(User.builder().kakaoId("k-profile").nickname("프로필있음")
				.profileImageKey("profiles/me.jpg").build());
		Clothing dress = em.persist(Clothing.builder()
				.owner(profileOwner).region(mapo).title("하객룩 네이비 원피스").description("한 번 입었어요")
				.category(ClothingCategory.DRESS).size(ClothingSize.M)
				.totalLength(new BigDecimal("105.0")).shoulderWidth(new BigDecimal("36.5"))
				.chestWidth(new BigDecimal("44.0")).waistWidth(new BigDecimal("34.0"))
				.hasDefect(true).defectDescription("밑단 올 풀림")
				.rentalFee(25000).deliveryAvailable(true).deliveryFee(3500)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build());
		em.persist(new ClothingColor(dress, Color.NAVY));
		em.persist(new ClothingColor(dress, Color.BEIGE));
		em.persist(ClothingUnavailablePeriod.builder().clothing(dress)
				.startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2027, 1, 3)).build());
		em.persist(ClothingUnavailablePeriod.builder().clothing(dress)
				.startDate(LocalDate.of(2026, 12, 24)).endDate(LocalDate.of(2026, 12, 26)).build());
		photo(dress, PhotoPart.DEFECT, 3, "clothes/defect.jpg");
		photo(dress, PhotoPart.FRONT, 0, "clothes/front.jpg");
		photo(dress, PhotoPart.BACK, 1, "clothes/back.jpg");
		photo(dress, PhotoPart.LABEL, 1, "clothes/label.jpg");
		em.flush();
		nativeUpdate("update clothes set created_at = ? where id = ?", BASE, dress.getId());
		flushAndClear();

		ClothingDetailResponse detail = queryService.getDetail(dress.getId());

		assertThat(detail.clothesId()).isEqualTo(dress.getId());
		assertThat(detail.status()).isEqualTo(ClothingStatus.AVAILABLE);
		assertThat(detail.title()).isEqualTo("하객룩 네이비 원피스");
		assertThat(detail.description()).isEqualTo("한 번 입었어요");
		assertThat(detail.category()).isEqualTo(ClothingCategory.DRESS);
		assertThat(detail.size()).isEqualTo(ClothingSize.M);
		assertThat(detail.measurement().totalLength()).isEqualByComparingTo("105.0");
		assertThat(detail.measurement().waistWidth()).isEqualByComparingTo("34.0");
		assertThat(detail.measurement().sleeveLength()).isNull();
		assertThat(detail.colors()).containsExactlyInAnyOrder(Color.NAVY, Color.BEIGE);
		assertThat(detail.hasDefect()).isTrue();
		assertThat(detail.defectDescription()).isEqualTo("밑단 올 풀림");
		assertThat(detail.region()).isEqualTo(new RegionResponse(MAPO, "서울", "마포구"));
		assertThat(detail.rentalFee()).isEqualTo(25000);
		assertThat(detail.deliveryAvailable()).isTrue();
		assertThat(detail.deliveryFee()).isEqualTo(3500);
		assertThat(detail.laundryBy()).isEqualTo(LaundryBy.BORROWER);
		assertThat(detail.laundryMethod()).isEqualTo(LaundryMethod.DRY_CLEAN);
		assertThat(detail.unavailablePeriods()).as("시작일 순 원본 구간").containsExactly(
				new ClothingDetailResponse.UnavailablePeriod(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26)),
				new ClothingDetailResponse.UnavailablePeriod(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3)));
		assertThat(detail.photos()).as("sortOrder, 등록 순").extracting(ClothingDetailResponse.Photo::photoPart)
				.containsExactly(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL, PhotoPart.DEFECT);
		assertThat(detail.photos().get(0).imageUrl()).isEqualTo("/local-images/clothes/front.jpg");
		assertThat(detail.photos().get(0).serverReceivedAt()).isEqualTo(BASE);
		assertThat(detail.owner()).isEqualTo(new ClothingDetailResponse.Owner(profileOwner.getId(), "프로필있음",
				"/local-images/profiles/me.jpg"));
		assertThat(detail.createdAt()).isEqualTo(BASE);
	}

	@Test
	@DisplayName("대여 중지 옷도 상세는 보이고 status가 PAUSED, 프로필 사진이 없으면 null")
	void 상세_대여_중지() {
		Clothing paused = clothing("대여 중지 옷", ClothingCategory.TOP, 0);
		nativeUpdate("update clothes set status = 'PAUSED' where id = ?", paused.getId());
		flushAndClear();

		ClothingDetailResponse detail = queryService.getDetail(paused.getId());

		assertThat(detail.status()).isEqualTo(ClothingStatus.PAUSED);
		assertThat(detail.owner().profileImageUrl()).isNull();
		assertThat(detail.photos()).isEmpty();
		assertThat(detail.unavailablePeriods()).isEmpty();
	}

	@Test
	@DisplayName("삭제된 옷이나 없는 옷이면 CLOTHING_404_1")
	void 상세_없음() {
		Clothing deleted = clothing("삭제된 옷", ClothingCategory.TOP, 0);
		nativeUpdate("update clothes set deleted_at = ? where id = ?", BASE, deleted.getId());
		flushAndClear();

		assertThatThrownBy(() -> queryService.getDetail(deleted.getId()))
				.isInstanceOf(ClothingException.class)
				.extracting("responseCode").isEqualTo(ClothingErrorCode.CLOTHING_NOT_FOUND);
		assertThatThrownBy(() -> queryService.getDetail(Long.MAX_VALUE))
				.isInstanceOf(ClothingException.class)
				.extracting("responseCode").isEqualTo(ClothingErrorCode.CLOTHING_NOT_FOUND);
	}

}
