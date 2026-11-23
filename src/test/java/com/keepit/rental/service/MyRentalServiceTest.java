package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.global.s3.FileUploader;
import com.keepit.region.entity.Region;
import com.keepit.rental.dto.MyRentalResponse;
import com.keepit.rental.dto.MyRentalRole;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.user.entity.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MyRentalServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 10, 0);

	private final RentalRepository rentalRepository = mock(RentalRepository.class);
	private final ClothingPhotoRepository clothingPhotoRepository = mock(ClothingPhotoRepository.class);
	private final FileUploader fileUploader = mock(FileUploader.class);
	private final MyRentalService service = new MyRentalService(rentalRepository, clothingPhotoRepository, fileUploader);

	private User owner;
	private User borrower;
	private Clothing dress;

	@BeforeEach
	void setUp() {
		owner = user(1L, "주인");
		borrower = user(2L, "대여자");
		dress = Clothing.builder()
				.owner(owner).region(Region.builder().sido("서울").sigungu("마포구").open(true).build())
				.title("하객룩 원피스").rentalFee(15000)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build();
		ReflectionTestUtils.setField(dress, "id", 10L);
		when(fileUploader.getUrl(anyString())).thenAnswer(inv -> "https://img/" + inv.getArgument(0));
	}

	private static User user(Long id, String nickname) {
		User user = User.builder().kakaoId("kakao-" + id).nickname(nickname).build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Rental rental(Long id) {
		Rental rental = Rental.request(dress, borrower,
				new RentalPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)), TradeMethod.DIRECT, NOW);
		ReflectionTestUtils.setField(rental, "id", id);
		return rental;
	}

	private ClothingPhoto front(String key, int sortOrder) {
		return ClothingPhoto.builder().clothing(dress).photoPart(PhotoPart.FRONT).imageKey(key).sortOrder(sortOrder)
				.serverReceivedAt(NOW).build();
	}

	@Test
	void 빌린_옷은_상대가_옷_주인이고_전면_첫_장이_대표_사진이다() {
		when(rentalRepository.findAllBorrowedBy(2L)).thenReturn(List.of(rental(55L)));
		when(clothingPhotoRepository.findByClothingIdsAndPart(List.of(10L), PhotoPart.FRONT))
				.thenReturn(List.of(front("clothes/first.jpg", 0), front("clothes/second.jpg", 1)));

		List<MyRentalResponse> result = service.getMyRentals(2L, MyRentalRole.BORROWER);

		assertThat(result).hasSize(1);
		MyRentalResponse card = result.get(0);
		assertThat(card.rentalId()).isEqualTo(55L);
		assertThat(card.thumbnailUrl()).isEqualTo("https://img/clothes/first.jpg");
		assertThat(card.regionName()).isEqualTo("마포구");
		assertThat(card.counterpartNickname()).isEqualTo("주인");
		assertThat(card.expiresAt()).isEqualTo(NOW.plusHours(48));
		verify(rentalRepository, never()).findAllLentBy(any());
	}

	@Test
	void 빌려준_옷은_상대가_대여자이고_사진이_없으면_null이다() {
		when(rentalRepository.findAllLentBy(1L)).thenReturn(List.of(rental(55L)));
		when(clothingPhotoRepository.findByClothingIdsAndPart(any(), eq(PhotoPart.FRONT))).thenReturn(List.of());

		MyRentalResponse card = service.getMyRentals(1L, MyRentalRole.OWNER).get(0);

		assertThat(card.counterpartNickname()).isEqualTo("대여자");
		assertThat(card.thumbnailUrl()).isNull();
	}

	@Test
	void 거래가_없으면_사진을_찾지_않고_빈_목록() {
		when(rentalRepository.findAllBorrowedBy(2L)).thenReturn(List.of());

		assertThat(service.getMyRentals(2L, MyRentalRole.BORROWER)).isEmpty();
		verify(clothingPhotoRepository, never()).findByClothingIdsAndPart(any(), any());
	}

}
