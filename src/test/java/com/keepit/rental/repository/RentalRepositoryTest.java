package com.keepit.rental.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.global.config.JpaConfig;
import com.keepit.region.entity.Region;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 직접 작성한 JPQL 쿼리가 실제 MySQL(Testcontainers)에서 의도대로 동작하는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class, RentalSupportRepository.class})
@ActiveProfiles("test")
class RentalRepositoryTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

	@Autowired
	private TestEntityManager em;
	@Autowired
	private RentalRepository rentalRepository;
	@Autowired
	private RentalSupportRepository supportRepository;

	private User owner;
	private User otherOwner;
	private User borrower;
	private Clothing dress;
	private Clothing jacket;
	private Clothing othersClothing;

	@BeforeEach
	void setUp() {
		owner = em.persist(User.builder().kakaoId("k-owner").nickname("주인").build());
		otherOwner = em.persist(User.builder().kakaoId("k-other").nickname("다른주인").build());
		borrower = em.persist(User.builder().kakaoId("k-borrower").nickname("대여자").build());
		Region region = em.persist(Region.builder().sido("테스트").sigungu("테스트구").open(true).build());
		dress = em.persist(clothing(owner, region, "원피스"));
		jacket = em.persist(clothing(owner, region, "자켓"));
		othersClothing = em.persist(clothing(otherOwner, region, "남의 옷"));
	}

	private static Clothing clothing(User owner, Region region, String title) {
		return Clothing.builder()
				.owner(owner).region(region).title(title)
				.category(ClothingCategory.TOP).size(ClothingSize.M).totalLength(new BigDecimal("60.0"))
				.rentalFee(10000).deliveryAvailable(false)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build();
	}

	private Rental rental(Clothing clothing, int startDay, int endDay, LocalDateTime requestedAt) {
		RentalPeriod period = new RentalPeriod(LocalDate.of(2026, 11, startDay), LocalDate.of(2026, 11, endDay));
		return em.persist(Rental.request(clothing, borrower, period, TradeMethod.DIRECT, requestedAt));
	}

	@Test
	void 받은_신청은_내_옷의_신청만_최신순으로_가져온다() {
		Rental older = rental(dress, 20, 21, NOW);
		Rental newer = rental(jacket, 22, 23, NOW.plusHours(1));
		rental(othersClothing, 20, 21, NOW.plusHours(2));
		em.flush();
		em.clear();

		List<Rental> result = rentalRepository.findReceivedRequests(owner.getId(), null, null);

		assertThat(result).extracting(Rental::getId).containsExactly(newer.getId(), older.getId());
	}

	@Test
	void 받은_신청은_옷과_상태로_거를_수_있다() {
		Rental dressRequested = rental(dress, 20, 21, NOW);
		Rental dressAccepted = rental(dress, 25, 26, NOW);
		dressAccepted.accept(NOW);
		rental(jacket, 20, 21, NOW);
		em.flush();
		em.clear();

		assertThat(rentalRepository.findReceivedRequests(owner.getId(), dress.getId(), null))
				.extracting(Rental::getId).containsExactlyInAnyOrder(dressRequested.getId(), dressAccepted.getId());
		assertThat(rentalRepository.findReceivedRequests(owner.getId(), dress.getId(), RentalStatus.ACCEPTED))
				.extracting(Rental::getId).containsExactly(dressAccepted.getId());
	}

	@Test
	void 받은_신청은_옷과_신청자를_한_번에_가져온다() {
		rental(dress, 20, 21, NOW);
		em.flush();
		em.clear();

		Rental found = rentalRepository.findReceivedRequests(owner.getId(), null, null).get(0);

		// fetch join이 빠지면 지연 로딩 프록시라 false가 된다 (목록에서 건마다 추가 쿼리 = N+1)
		assertThat(Hibernate.isInitialized(found.getClothing())).isTrue();
		assertThat(Hibernate.isInitialized(found.getBorrower())).isTrue();
	}

	@Test
	void 같은_옷의_특정_상태_대여만_가져온다() {
		Rental requested = rental(dress, 20, 21, NOW);
		Rental accepted = rental(dress, 25, 26, NOW);
		accepted.accept(NOW);
		rental(jacket, 20, 21, NOW).accept(NOW);
		em.flush();
		em.clear();

		assertThat(rentalRepository.findAllByClothingIdAndStatusIn(dress.getId(), EnumSet.of(RentalStatus.ACCEPTED)))
				.extracting(Rental::getId).containsExactly(accepted.getId());
		assertThat(rentalRepository.findAllByClothingIdAndStatusIn(dress.getId(), EnumSet.of(RentalStatus.REQUESTED)))
				.extracting(Rental::getId).containsExactly(requested.getId());
	}

	@Test
	void 불가_기간은_그_옷의_것만_기간으로_바꿔_가져온다() {
		em.persist(ClothingUnavailablePeriod.builder().clothing(dress)
				.startDate(LocalDate.of(2026, 12, 24)).endDate(LocalDate.of(2026, 12, 26)).build());
		em.persist(ClothingUnavailablePeriod.builder().clothing(jacket)
				.startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 1)).build());
		em.flush();
		em.clear();

		assertThat(supportRepository.findUnavailablePeriods(dress.getId()))
				.containsExactly(new RentalPeriod(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26)));
	}

	@Test
	void 옷_행을_잠그며_조회할_수_있다() {
		em.flush();
		em.clear();

		assertThat(supportRepository.findClothingForUpdate(dress.getId()))
				.get().extracting(Clothing::getTitle).isEqualTo("원피스");
	}

	@Test
	void 빌린_거래는_내가_신청한_것만_최신순이고_옷_지역_주인을_함께_가져온다() {
		User stranger = em.persist(User.builder().kakaoId("k-stranger").nickname("남").build());
		Rental older = rental(dress, 20, 21, NOW);
		Rental newer = rental(othersClothing, 22, 23, NOW.plusHours(1));
		em.persist(Rental.request(jacket, stranger,
				new RentalPeriod(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 26)), TradeMethod.DIRECT, NOW));
		em.flush();
		em.clear();

		List<Rental> result = rentalRepository.findAllBorrowedBy(borrower.getId());

		assertThat(result).extracting(Rental::getId).containsExactly(newer.getId(), older.getId());
		assertThat(Hibernate.isInitialized(result.get(0).getClothing().getRegion())).isTrue();
		assertThat(Hibernate.isInitialized(result.get(0).getOwner())).isTrue();
	}

	@Test
	void 빌려준_거래는_내_옷의_거래만_가져오고_대여자를_함께_가져온다() {
		Rental mine = rental(dress, 20, 21, NOW);
		rental(othersClothing, 20, 21, NOW);
		em.flush();
		em.clear();

		List<Rental> result = rentalRepository.findAllLentBy(owner.getId());

		assertThat(result).extracting(Rental::getId).containsExactly(mine.getId());
		assertThat(Hibernate.isInitialized(result.get(0).getBorrower())).isTrue();
	}

}
