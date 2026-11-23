package com.keepit.rental.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.global.config.JpaConfig;
import com.keepit.region.entity.Region;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalPhoto;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 체크포인트 사진 쿼리(단계별 부위 조회·단계 삭제)가 실제 MySQL에서 의도대로 동작하는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class RentalPhotoRepositoryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

	@Autowired
	private TestEntityManager em;
	@Autowired
	private RentalPhotoRepository rentalPhotoRepository;

	private User owner;
	private Rental rental;
	private Rental otherRental;

	@BeforeEach
	void setUp() {
		owner = em.persist(User.builder().kakaoId("k-owner").nickname("주인").build());
		User borrower = em.persist(User.builder().kakaoId("k-borrower").nickname("대여자").build());
		Region region = em.persist(Region.builder().sido("테스트").sigungu("테스트구").open(true).build());
		Clothing dress = em.persist(Clothing.builder()
				.owner(owner).region(region).title("원피스")
				.category(ClothingCategory.TOP).size(ClothingSize.M).totalLength(new BigDecimal("60.0"))
				.rentalFee(10000).deliveryAvailable(false)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build());
		rental = em.persist(Rental.request(dress, borrower,
				new RentalPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21)), TradeMethod.DIRECT, NOW));
		otherRental = em.persist(Rental.request(dress, borrower,
				new RentalPeriod(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26)), TradeMethod.DIRECT, NOW));
	}

	private void photo(Rental rental, RentalPhotoStage stage, PhotoPart part, int sortOrder) {
		em.persist(RentalPhoto.builder().rental(rental).uploader(owner).stage(stage).photoPart(part)
				.imageKey("rentals/" + rental.getId() + "-" + stage + "-" + part + ".jpg")
				.sortOrder(sortOrder).serverReceivedAt(NOW).build());
	}

	@Test
	void 단계_부위는_그_거래_그_단계_사진만_가져온다() {
		photo(rental, RentalPhotoStage.HANDOVER, PhotoPart.FRONT, 0);
		photo(rental, RentalPhotoStage.HANDOVER, PhotoPart.BACK, 1);
		photo(rental, RentalPhotoStage.RECEIVE, PhotoPart.LABEL, 0);
		photo(otherRental, RentalPhotoStage.HANDOVER, PhotoPart.LABEL, 0);
		em.flush();
		em.clear();

		assertThat(rentalPhotoRepository.findPartsOfStage(rental.getId(), RentalPhotoStage.HANDOVER))
				.containsExactlyInAnyOrder(PhotoPart.FRONT, PhotoPart.BACK);
	}

	@Test
	void 단계_삭제는_그_거래_그_단계만_지운다() {
		photo(rental, RentalPhotoStage.HANDOVER, PhotoPart.FRONT, 0);
		photo(rental, RentalPhotoStage.RECEIVE, PhotoPart.FRONT, 0);
		photo(otherRental, RentalPhotoStage.HANDOVER, PhotoPart.FRONT, 0);
		em.flush();
		em.clear();

		int deleted = rentalPhotoRepository.deleteStage(rental.getId(), RentalPhotoStage.HANDOVER);

		assertThat(deleted).isEqualTo(1);
		assertThat(rentalPhotoRepository.findAllOfRental(rental.getId()))
				.extracting(RentalPhoto::getStage).containsExactly(RentalPhotoStage.RECEIVE);
		assertThat(rentalPhotoRepository.findAllOfRental(otherRental.getId())).hasSize(1);
	}

}
