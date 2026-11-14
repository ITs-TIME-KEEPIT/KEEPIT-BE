package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.region.entity.Region;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 같은 옷의 겹치는 두 신청을 동시에 수락해도 하나만 수락되는지, 실제 MySQL 잠금으로 확인한다.
 * 스레드마다 별도 트랜잭션이 필요해서 @DataJpaTest(테스트 전체가 한 트랜잭션) 대신 @SpringBootTest를 쓰고,
 * 끝나면 직접 데이터를 지운다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RentalAcceptConcurrencyTest {

	@Autowired
	private RentalService rentalService;
	@Autowired
	private RentalRepository rentalRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private EntityManager em;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from rental_status_logs");
		jdbcTemplate.update("delete from rentals");
		jdbcTemplate.update("delete from clothes");
		jdbcTemplate.update("delete from regions");
		jdbcTemplate.update("delete from users");
	}

	@Test
	void 겹치는_두_신청을_동시에_수락해도_하나만_수락된다() throws Exception {
		Long[] ids = transactionTemplate.execute(status -> {
			User owner = User.builder().kakaoId("k-owner").nickname("주인").build();
			User first = User.builder().kakaoId("k-first").nickname("대여자1").build();
			User second = User.builder().kakaoId("k-second").nickname("대여자2").build();
			Region region = Region.builder().sido("서울").sigungu("마포구").open(true).build();
			em.persist(owner);
			em.persist(first);
			em.persist(second);
			em.persist(region);
			Clothing clothing = Clothing.builder()
					.owner(owner).region(region).title("원피스")
					.category(ClothingCategory.DRESS).size(ClothingSize.M).totalLength(new BigDecimal("100.0"))
					.rentalFee(20000).deliveryAvailable(false)
					.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
					.build();
			em.persist(clothing);
			return new Long[] {owner.getId(), first.getId(), second.getId(), clothing.getId()};
		});
		Long ownerId = ids[0];
		LocalDate base = LocalDate.now().plusDays(10);
		Long rentalA = rentalService.request(ids[1], ids[3], new RentalPeriod(base, base.plusDays(2)),
				TradeMethod.DIRECT, true);
		Long rentalB = rentalService.request(ids[2], ids[3], new RentalPeriod(base.plusDays(1), base.plusDays(3)),
				TradeMethod.DIRECT, true);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<Boolean>> results = new ArrayList<>();
		for (Long rentalId : List.of(rentalA, rentalB)) {
			Callable<Boolean> accept = () -> {
				ready.countDown();
				start.await();
				try {
					rentalService.accept(ownerId, rentalId);
					return true;
				} catch (RuntimeException e) {
					return false;   // 나중에 들어온 쪽은 상태 오류나 예약 겹침으로 거절된다
				}
			};
			results.add(executor.submit(accept));
		}
		ready.await();
		start.countDown();   // 두 스레드를 동시에 출발시킨다
		int succeeded = 0;
		for (Future<Boolean> result : results) {
			if (result.get(30, TimeUnit.SECONDS)) {
				succeeded++;
			}
		}
		executor.shutdown();

		assertThat(succeeded).isEqualTo(1);
		List<RentalStatus> statuses = List.of(
				rentalRepository.findById(rentalA).orElseThrow().getStatus(),
				rentalRepository.findById(rentalB).orElseThrow().getStatus());
		assertThat(statuses).containsExactlyInAnyOrder(RentalStatus.ACCEPTED, RentalStatus.INVALIDATED);
	}

}
