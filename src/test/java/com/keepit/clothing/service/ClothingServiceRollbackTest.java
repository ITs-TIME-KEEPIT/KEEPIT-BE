package com.keepit.clothing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingPhotoRequest;
import com.keepit.clothing.dto.MeasurementRequest;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.image.entity.Image;
import com.keepit.region.entity.Region;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 옷 등록 트랜잭션의 롤백 경계를 실제 MySQL로 확인한다.
 * 이미지 연결(조건부 UPDATE) 뒤에 지역 확인이 실패해도, 서비스 트랜잭션이 롤백되어 이미지가 미연결로 돌아가야 한다.
 *
 * <p>@DataJpaTest는 테스트 전체가 한 트랜잭션이라 서비스 자체의 롤백을 볼 수 없어서 @SpringBootTest를 쓰고
 * 테스트에는 트랜잭션을 걸지 않는다. 같은 설정의 다른 테스트와 DB를 공유하므로 시드 지역에 기대지 않고
 * 직접 만든 지역을 쓰며, 끝나면 이 테스트가 만든 행만 id로 지운다.
 * 사용자 id는 서비스 인자로 넘기므로 특정 회원 id에 의존하지 않는다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ClothingServiceRollbackTest {

	private static final String NOT_EXISTING_REGION_SQL = "select coalesce(max(id), 0) + 1000 from regions";

	@Autowired
	private ClothingService clothingService;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private EntityManager em;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long ownerId;
	private Integer regionId;
	private final List<Long> imageIds = new ArrayList<>();
	private final List<Long> clothingIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		transactionTemplate.executeWithoutResult(status -> {
			User owner = User.builder().kakaoId("k-rollback-owner").nickname("롤백확인").build();
			em.persist(owner);
			Region region = Region.builder().sido("테스트").sigungu("롤백구").open(true).build();
			em.persist(region);
			for (String part : List.of("front", "back", "label")) {
				Image image = Image.builder()
						.uploader(owner)
						.imageKey("clothes/rollback-" + part + ".jpg")
						.serverReceivedAt(LocalDateTime.of(2026, 11, 19, 13, 55))
						.build();
				em.persist(image);
				imageIds.add(image.getId());
			}
			ownerId = owner.getId();
			regionId = region.getId();
		});
	}

	/** 이 테스트가 만든 행만 id로 지운다(외래 키 순서: 사진·컬러·기간 → 옷 → 이미지 → 지역·사용자). */
	@AfterEach
	void cleanUp() {
		for (Long clothingId : clothingIds) {
			jdbcTemplate.update("delete from clothing_photos where clothing_id = ?", clothingId);
			jdbcTemplate.update("delete from clothing_colors where clothing_id = ?", clothingId);
			jdbcTemplate.update("delete from clothing_unavailable_periods where clothing_id = ?", clothingId);
			jdbcTemplate.update("delete from clothes where id = ?", clothingId);
		}
		for (Long imageId : imageIds) {
			jdbcTemplate.update("delete from images where id = ?", imageId);
		}
		if (regionId != null) {
			jdbcTemplate.update("delete from regions where id = ?", regionId);
		}
		if (ownerId != null) {
			jdbcTemplate.update("delete from users where id = ?", ownerId);
		}
	}

	private ClothingCreateRequest request(Integer regionId) {
		return new ClothingCreateRequest("롤백 확인 셔츠", null, ClothingCategory.TOP, ClothingSize.M, List.of(Color.NAVY),
				new MeasurementRequest(new BigDecimal("60.0"), null, null, null, null, null, null, null),
				false, null, regionId, 10000, false, null, LaundryBy.BORROWER, null, null,
				List.of(new ClothingPhotoRequest(imageIds.get(0), PhotoPart.FRONT, 0),
						new ClothingPhotoRequest(imageIds.get(1), PhotoPart.BACK, 1),
						new ClothingPhotoRequest(imageIds.get(2), PhotoPart.LABEL, 2)));
	}

	private List<Boolean> linkedFlags() {
		return transactionTemplate.execute(status -> imageIds.stream()
				.map(id -> em.find(Image.class, id).isLinked())
				.toList());
	}

	private Integer clothesCountOfOwner() {
		return jdbcTemplate.queryForObject("select count(*) from clothes where owner_id = ?", Integer.class, ownerId);
	}

	@Test
	@DisplayName("이미지를 연결한 뒤 지역 확인에서 실패하면 연결까지 롤백되고, 같은 이미지로 다시 등록할 수 있다")
	void 이미지_연결_후_실패하면_롤백() {
		Integer notExistingRegionId = jdbcTemplate.queryForObject(NOT_EXISTING_REGION_SQL, Integer.class);

		assertThatThrownBy(() -> clothingService.register(ownerId, request(notExistingRegionId)))
				.isInstanceOf(ClothingException.class)
				.extracting("responseCode").isEqualTo(ClothingErrorCode.REGION_NOT_SELECTABLE);

		assertThat(linkedFlags()).as("서비스 트랜잭션이 끝난 뒤 새 트랜잭션에서 본 연결 상태").containsOnly(false);
		assertThat(clothesCountOfOwner()).isZero();

		Long clothingId = clothingService.register(ownerId, request(regionId));
		clothingIds.add(clothingId);

		assertThat(linkedFlags()).containsOnly(true);
		assertThat(clothesCountOfOwner()).isEqualTo(1);
	}

}
