package com.keepit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingColorId;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.global.config.JpaConfig;
import com.keepit.holiday.entity.Holiday;
import com.keepit.notification.entity.Notification;
import com.keepit.notification.entity.NotificationType;
import com.keepit.region.entity.Region;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPhoto;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.entity.RentalStatusLog;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.report.entity.DamageClaim;
import com.keepit.report.entity.Report;
import com.keepit.report.entity.ReportStatus;
import com.keepit.report.entity.ReportType;
import com.keepit.review.entity.Review;
import com.keepit.review.entity.ReviewKeyword;
import com.keepit.review.entity.ReviewKeywordId;
import com.keepit.review.entity.ReviewSatisfaction;
import com.keepit.user.entity.RefreshToken;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserRole;
import com.keepit.user.entity.UserStatus;
import jakarta.persistence.PersistenceException;
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
 * Flyway V1 스키마 위에서 Entity 매핑을 검증한다.
 * (컨텍스트 기동 시 ddl-auto: validate로 컬럼 타입·누락 검증, 이후 실제 insert/select와 제약조건 검증)
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class EntityMappingTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 12, 0);

	@Autowired
	private TestEntityManager em;

	private User owner;
	private User borrower;
	private Clothing clothing;
	private Rental rental;

	@BeforeEach
	void setUp() {
		owner = em.persist(user("kakao-owner", "소유자"));
		borrower = em.persist(user("kakao-borrower", "대여자"));
		Region region = em.persist(Region.builder().sido("서울").sigungu("마포구").open(true).build());
		clothing = em.persist(Clothing.builder()
				.owner(owner)
				.region(region)
				.title("트위드 자켓")
				.category(ClothingCategory.OUTER)
				.size(ClothingSize.M)
				.totalLength(new BigDecimal("65.5"))
				.rentalFee(15000)
				.deliveryAvailable(true)
				.deliveryFee(3000)
				.laundryBy(LaundryBy.BORROWER)
				.laundryMethod(LaundryMethod.DRY_CLEAN)
				.build());
		rental = em.persist(Rental.builder()
				.clothing(clothing)
				.owner(owner)
				.borrower(borrower)
				.startDate(LocalDate.of(2026, 11, 10))
				.endDate(LocalDate.of(2026, 11, 12))
				.tradeMethod(TradeMethod.DELIVERY)
				.appliedRentalFee(15000)
				.appliedDeliveryFee(3000)
				.appliedLaundryBy(LaundryBy.BORROWER)
				.appliedLaundryMethod(LaundryMethod.DRY_CLEAN)
				.rulesAgreedAt(NOW)
				.requestedAt(NOW)
				.expiresAt(NOW.plusHours(24))
				.build());
	}

	@Test
	@DisplayName("모든 Entity를 저장·조회할 수 있고 created_at/updated_at이 자동 기록된다")
	void persistAllEntities() {
		Report report = em.persist(Report.builder()
				.rental(rental).reporter(owner).type(ReportType.DAMAGE_WASHABLE).content("얼룩이 생겼어요").build());
		Review review = em.persist(Review.builder()
				.rental(rental).writer(borrower).target(owner).satisfaction(ReviewSatisfaction.BEST).build());

		em.persist(new ClothingColor(clothing, Color.NAVY));
		em.persist(ClothingPhoto.builder().clothing(clothing).photoPart(PhotoPart.FRONT)
				.imageKey("clothes/1/front.jpg").sortOrder(0).serverReceivedAt(NOW).build());
		em.persist(ClothingUnavailablePeriod.builder().clothing(clothing)
				.startDate(LocalDate.of(2026, 12, 24)).endDate(LocalDate.of(2026, 12, 26)).build());
		em.persist(RentalPhoto.builder().rental(rental).uploader(owner).stage(RentalPhotoStage.HANDOVER)
				.photoPart(PhotoPart.DEFECT).imageKey("rentals/1/handover.jpg").sortOrder(0)
				.serverReceivedAt(NOW).build());
		em.persist(RentalStatusLog.builder().rental(rental).toStatus(RentalStatus.REQUESTED)
				.actorType(ActorType.BORROWER).actor(borrower).build());
		em.persist(RentalStatusLog.builder().rental(rental).fromStatus(RentalStatus.REQUESTED)
				.toStatus(RentalStatus.EXPIRED).actorType(ActorType.SYSTEM).build());
		em.persist(Notification.builder().user(owner).rental(rental).type(NotificationType.REQUEST_ARRIVED).build());
		em.persist(Notification.builder().user(owner).type(NotificationType.ADMIN_STATUS_CHANGED).build());
		em.persist(DamageClaim.builder().report(report).claimAmount(20000).build());
		em.persist(new ReviewKeyword(review, "친절해요"));
		em.persist(RefreshToken.builder().user(owner).tokenHash("a".repeat(64)).expiresAt(NOW.plusDays(14)).build());
		em.persist(Holiday.builder().name("설날").startDate(LocalDate.of(2027, 2, 6))
				.endDate(LocalDate.of(2027, 2, 9)).deliveryCutoffDate(LocalDate.of(2027, 2, 3)).build());
		em.flush();
		em.clear();

		Rental foundRental = em.find(Rental.class, rental.getId());
		assertThat(foundRental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
		assertThat(foundRental.getClothing().getId()).isEqualTo(clothing.getId());
		assertThat(foundRental.getCreatedAt()).isNotNull();
		assertThat(foundRental.getUpdatedAt()).isNotNull();

		Clothing foundClothing = em.find(Clothing.class, clothing.getId());
		assertThat(foundClothing.getTotalLength()).isEqualByComparingTo("65.5");
		assertThat(foundClothing.getShoulderWidth()).isNull();

		ClothingColor color = em.find(ClothingColor.class, new ClothingColorId(clothing.getId(), Color.NAVY));
		assertThat(color.getColor()).isEqualTo(Color.NAVY);
		assertThat(color.getCreatedAt()).isNotNull();

		ReviewKeyword keyword = em.find(ReviewKeyword.class, new ReviewKeywordId(review.getId(), "친절해요"));
		assertThat(keyword.getKeyword()).isEqualTo("친절해요");

		Report foundReport = em.find(Report.class, report.getId());
		assertThat(foundReport.getStatus()).isEqualTo(ReportStatus.RECEIVED);

		User foundOwner = em.find(User.class, owner.getId());
		assertThat(foundOwner.getRole()).isEqualTo(UserRole.USER);
		assertThat(foundOwner.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

	@Test
	@DisplayName("enum은 VARCHAR 컬럼에 상수 이름 문자열로 저장된다")
	void enumStoredAsName() {
		em.flush();

		Object status = em.getEntityManager()
				.createNativeQuery("SELECT status FROM rentals WHERE id = :id")
				.setParameter("id", rental.getId())
				.getSingleResult();
		Object laundryMethod = em.getEntityManager()
				.createNativeQuery("SELECT laundry_method FROM clothes WHERE id = :id")
				.setParameter("id", clothing.getId())
				.getSingleResult();

		assertThat(status).isEqualTo("REQUESTED");
		assertThat(laundryMethod).isEqualTo("DRY_CLEAN");
	}

	@Test
	@DisplayName("시스템 액터의 상태 로그와 대여 무관 알림은 FK를 null로 저장할 수 있다")
	void nullableForeignKeys() {
		RentalStatusLog log = em.persistFlushFind(RentalStatusLog.builder().rental(rental)
				.fromStatus(RentalStatus.REQUESTED).toStatus(RentalStatus.EXPIRED).actorType(ActorType.SYSTEM).build());
		Notification notification = em.persistFlushFind(
				Notification.builder().user(owner).type(NotificationType.ADMIN_STATUS_CHANGED).build());

		assertThat(log.getActor()).isNull();
		assertThat(notification.getRental()).isNull();
		assertThat(notification.isRead()).isFalse();
	}

	@Test
	@DisplayName("같은 kakao_id로 사용자를 중복 저장할 수 없다")
	void duplicateKakaoId() {
		em.flush();

		assertThatThrownBy(() -> em.persistAndFlush(user("kakao-owner", "다른닉네임")))
				.isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("같은 시도와 시군구로 지역을 중복 저장할 수 없다")
	void duplicateRegion() {
		em.flush();

		assertThatThrownBy(() -> em.persistAndFlush(
				Region.builder().sido("서울").sigungu("마포구").open(false).build()))
				.isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("refresh token은 사용자당 1개만 저장할 수 있다")
	void duplicateRefreshTokenPerUser() {
		em.persistAndFlush(RefreshToken.builder().user(owner).tokenHash("a".repeat(64)).expiresAt(NOW).build());

		assertThatThrownBy(() -> em.persistAndFlush(
				RefreshToken.builder().user(owner).tokenHash("b".repeat(64)).expiresAt(NOW).build()))
				.isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("같은 대여 건에 같은 작성자가 리뷰를 두 번 남길 수 없다")
	void duplicateReviewPerWriter() {
		em.persistAndFlush(Review.builder()
				.rental(rental).writer(borrower).target(owner).satisfaction(ReviewSatisfaction.GOOD).build());

		assertThatThrownBy(() -> em.persistAndFlush(Review.builder()
				.rental(rental).writer(borrower).target(owner).satisfaction(ReviewSatisfaction.BAD).build()))
				.isInstanceOf(PersistenceException.class);
	}

	@Test
	@DisplayName("복합 PK Entity는 저장 전에는 신규, 저장 후에는 기존 Entity로 판단된다")
	void compositeKeyIsNew() {
		ClothingColor color = new ClothingColor(clothing, Color.BLACK);
		assertThat(color.isNew()).isTrue();

		em.persistAndFlush(color);

		assertThat(color.isNew()).isFalse();
	}

	private static User user(String kakaoId, String nickname) {
		return User.builder().kakaoId(kakaoId).nickname(nickname).build();
	}

}
