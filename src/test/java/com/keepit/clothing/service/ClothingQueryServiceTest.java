package com.keepit.clothing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.dto.ClothingCardResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.dto.RegionResponse;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.global.config.JpaConfig;
import com.keepit.global.s3.LocalFileUploader;
import com.keepit.region.entity.Region;
import com.keepit.user.entity.User;
import java.math.BigDecimal;
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
	@DisplayName("대여 가능·미삭제 옷만 최신 등록순으로 주고, 카드 필드를 채운다")
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

		ClothingListResponse response = queryService.getList(null, 0, 20);

		assertThat(response.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(newer.getId(), older.getId());
		assertThat(response.hasNext()).isFalse();
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

		assertThat(queryService.getList(ClothingCategory.DRESS, 0, 20).content())
				.extracting(ClothingCardResponse::clothesId)
				.containsExactly(dress.getId());
	}

	@Test
	@DisplayName("size만큼 잘라 주고 다음 페이지가 있으면 hasNext가 true")
	void 페이지네이션() {
		Clothing first = clothing("1", ClothingCategory.TOP, 0);
		Clothing second = clothing("2", ClothingCategory.TOP, 1);
		Clothing third = clothing("3", ClothingCategory.TOP, 2);
		flushAndClear();

		ClothingListResponse page0 = queryService.getList(null, 0, 2);
		ClothingListResponse page1 = queryService.getList(null, 1, 2);

		assertThat(page0.content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(third.getId(), second.getId());
		assertThat(page0.hasNext()).isTrue();
		assertThat(page0.page()).isZero();
		assertThat(page0.size()).isEqualTo(2);
		assertThat(page1.content()).extracting(ClothingCardResponse::clothesId).containsExactly(first.getId());
		assertThat(page1.hasNext()).isFalse();
	}

	@Test
	@DisplayName("등록 시각이 같으면 id가 큰 옷이 먼저 온다")
	void 같은_시각은_id_내림차순() {
		Clothing a = clothing("a", ClothingCategory.TOP, 5);
		Clothing b = clothing("b", ClothingCategory.TOP, 5);
		flushAndClear();

		assertThat(queryService.getList(null, 0, 20).content()).extracting(ClothingCardResponse::clothesId)
				.containsExactly(b.getId(), a.getId());
	}

	@Test
	@DisplayName("조건에 맞는 옷이 없으면 빈 목록")
	void 빈_목록() {
		ClothingListResponse response = queryService.getList(ClothingCategory.ACC, 0, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.hasNext()).isFalse();
	}

}
