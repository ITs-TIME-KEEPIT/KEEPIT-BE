package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.global.config.ClockConfig;
import com.keepit.global.s3.FileUploader;
import com.keepit.image.entity.Image;
import com.keepit.image.repository.ImageRepository;
import com.keepit.rental.dto.CheckpointPhotoUploadRequest.Photo;
import com.keepit.rental.dto.RentalCheckpointResponse;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.rental.entity.RentalPhoto;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.TradeMethod;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.repository.RentalPhotoRepository;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.rental.repository.RentalSupportRepository;
import com.keepit.user.entity.User;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RentalCheckpointServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 13, 52);
	private static final Clock CLOCK = Clock.fixed(NOW.atZone(ClockConfig.SEOUL).toInstant(), ClockConfig.SEOUL);
	private static final Long CLOTHING_ID = 10L;
	private static final Long RENTAL_ID = 1L;
	private static final Long OWNER_ID = 1L;
	private static final Long BORROWER_ID = 2L;

	@Mock
	private RentalRepository rentalRepository;
	@Mock
	private RentalSupportRepository supportRepository;
	@Mock
	private RentalPhotoRepository rentalPhotoRepository;
	@Mock
	private ImageRepository imageRepository;
	@Mock
	private ClothingPhotoRepository clothingPhotoRepository;
	@Mock
	private FileUploader fileUploader;

	private RentalCheckpointService service;
	private Clothing clothing;
	private Rental rental;

	@BeforeEach
	void setUp() {
		service = new RentalCheckpointService(rentalRepository, supportRepository, rentalPhotoRepository,
				imageRepository, clothingPhotoRepository, fileUploader, CLOCK);
		User owner = user(OWNER_ID);
		User borrower = user(BORROWER_ID);
		clothing = Clothing.builder()
				.owner(owner).title("하객룩 원피스").rentalFee(15000)
				.laundryBy(LaundryBy.BORROWER).laundryMethod(LaundryMethod.DRY_CLEAN)
				.build();
		ReflectionTestUtils.setField(clothing, "id", CLOTHING_ID);
		rental = Rental.request(clothing, borrower,
				new RentalPeriod(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)), TradeMethod.DIRECT,
				NOW.minusDays(1));
		ReflectionTestUtils.setField(rental, "id", RENTAL_ID);
		rental.accept(NOW.minusHours(1));

		when(rentalRepository.findClothingIdById(RENTAL_ID)).thenReturn(Optional.of(CLOTHING_ID));
		when(supportRepository.findClothingForUpdate(CLOTHING_ID)).thenReturn(Optional.of(clothing));
		when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
		when(fileUploader.getUrl(anyString())).thenAnswer(inv -> "https://img/" + inv.getArgument(0));
	}

	private static User user(Long id) {
		User user = User.builder().kakaoId("kakao-" + id).nickname("회원" + id).build();
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private static List<Photo> threePhotos() {
		return List.of(new Photo(55L, PhotoPart.FRONT), new Photo(56L, PhotoPart.BACK), new Photo(57L, PhotoPart.LABEL));
	}

	/** 업로드 API로 올라온 이미지들이 모두 연결 가능하다고 해 둔다. */
	private void givenUsableImages(Long uploaderId, Long... ids) {
		when(imageRepository.link(anyCollection(), eq(uploaderId), any())).thenReturn(ids.length);
		when(imageRepository.findAllById(anyIterable())).thenReturn(java.util.Arrays.stream(ids).map(id -> {
			Image image = Image.builder().imageKey("rentals/" + id + ".jpg").serverReceivedAt(NOW.minusMinutes(id))
					.build();
			ReflectionTestUtils.setField(image, "id", id);
			return image;
		}).toList());
	}

	private static void assertError(Runnable action, RentalErrorCode expected) {
		assertThatThrownBy(action::run).extracting("responseCode").isEqualTo(expected);
	}

	@Nested
	class 사진_등록 {

		@Test
		@SuppressWarnings("unchecked")
		void 주인이_전달_직전_사진을_올리면_기존_사진을_지우고_순서대로_저장한다() {
			givenUsableImages(OWNER_ID, 55L, 56L, 57L);

			service.uploadPhotos(OWNER_ID, RENTAL_ID, RentalPhotoStage.HANDOVER, threePhotos());

			// 옷을 잠근 뒤 → 기존 단계 사진 삭제 → 이미지 연결 → 저장 순서
			InOrder order = inOrder(supportRepository, rentalPhotoRepository, imageRepository);
			order.verify(supportRepository).findClothingForUpdate(CLOTHING_ID);
			order.verify(rentalPhotoRepository).deleteStage(RENTAL_ID, RentalPhotoStage.HANDOVER);
			order.verify(imageRepository).link(List.of(55L, 56L, 57L), OWNER_ID, NOW);
			ArgumentCaptor<List<RentalPhoto>> saved = ArgumentCaptor.forClass(List.class);
			order.verify(rentalPhotoRepository).saveAll(saved.capture());

			assertThat(saved.getValue()).extracting(RentalPhoto::getPhotoPart, RentalPhoto::getSortOrder,
							RentalPhoto::getImageKey, RentalPhoto::getStage)
					.containsExactly(
							org.assertj.core.groups.Tuple.tuple(PhotoPart.FRONT, 0, "rentals/55.jpg", RentalPhotoStage.HANDOVER),
							org.assertj.core.groups.Tuple.tuple(PhotoPart.BACK, 1, "rentals/56.jpg", RentalPhotoStage.HANDOVER),
							org.assertj.core.groups.Tuple.tuple(PhotoPart.LABEL, 2, "rentals/57.jpg", RentalPhotoStage.HANDOVER));
			// 촬영 시각은 서버가 이미지를 받은 시각
			assertThat(saved.getValue().get(0).getServerReceivedAt()).isEqualTo(NOW.minusMinutes(55));
			assertThat(saved.getValue().get(0).getUploader().getId()).isEqualTo(OWNER_ID);
		}

		@Test
		void 전달_직전_사진은_대여자가_올릴_수_없다() {
			assertError(() -> service.uploadPhotos(BORROWER_ID, RENTAL_ID, RentalPhotoStage.HANDOVER, threePhotos()),
					RentalErrorCode.RENTAL_ACCESS_DENIED);
			verify(rentalPhotoRepository, never()).saveAll(any());
		}

		@Test
		void 지금_단계가_아닌_사진은_올릴_수_없다() {
			// 아직 수락 상태라 수령 사진은 이르다
			assertError(() -> service.uploadPhotos(BORROWER_ID, RENTAL_ID, RentalPhotoStage.RECEIVE, threePhotos()),
					RentalErrorCode.INVALID_RENTAL_STATUS);
		}

		@Test
		void 전달된_뒤에는_대여자가_수령_사진을_올린다() {
			rental.handOver(NOW);
			givenUsableImages(BORROWER_ID, 55L, 56L, 57L);

			service.uploadPhotos(BORROWER_ID, RENTAL_ID, RentalPhotoStage.RECEIVE, threePhotos());

			verify(rentalPhotoRepository).deleteStage(RENTAL_ID, RentalPhotoStage.RECEIVE);
			verify(rentalPhotoRepository).saveAll(any());
		}

		@Test
		void 필수_부위가_빠지면_저장하지_않는다() {
			assertError(() -> service.uploadPhotos(OWNER_ID, RENTAL_ID, RentalPhotoStage.HANDOVER,
							List.of(new Photo(55L, PhotoPart.FRONT), new Photo(56L, PhotoPart.BACK))),
					RentalErrorCode.CHECKPOINT_PHOTO_PART_MISSING);
			verify(imageRepository, never()).link(any(), any(), any());
		}

		@Test
		void 남의_사진이나_이미_쓴_사진이_섞이면_막는다() {
			when(imageRepository.link(anyCollection(), eq(OWNER_ID), any())).thenReturn(2);

			assertError(() -> service.uploadPhotos(OWNER_ID, RENTAL_ID, RentalPhotoStage.HANDOVER, threePhotos()),
					RentalErrorCode.CHECKPOINT_IMAGE_UNUSABLE);
			verify(rentalPhotoRepository, never()).saveAll(any());
		}

		@Test
		void 세탁_후_사진_단계로는_올릴_수_없다() {
			assertError(() -> service.uploadPhotos(BORROWER_ID, RENTAL_ID, RentalPhotoStage.RETURN_WASHED,
					threePhotos()), RentalErrorCode.CHECKPOINT_STAGE_NOT_UPLOADABLE);
		}

	}

	@Nested
	class 사진_비교 {

		@Test
		void 등록_전달_수령_반납_4단계를_순서대로_주고_안_찍은_단계는_비어_있다() {
			ClothingPhoto registered = ClothingPhoto.builder().clothing(clothing).photoPart(PhotoPart.FRONT)
					.imageKey("clothes/front.jpg").sortOrder(0).serverReceivedAt(NOW.minusDays(13)).build();
			when(clothingPhotoRepository.findAllOfClothing(CLOTHING_ID)).thenReturn(List.of(registered));
			RentalPhoto handover = RentalPhoto.builder().rental(rental).uploader(rental.getOwner())
					.stage(RentalPhotoStage.HANDOVER).photoPart(PhotoPart.FRONT).imageKey("rentals/h.jpg")
					.sortOrder(0).serverReceivedAt(NOW).build();
			when(rentalPhotoRepository.findAllOfRental(RENTAL_ID)).thenReturn(List.of(handover));

			RentalCheckpointResponse response = service.getCheckpoints(BORROWER_ID, RENTAL_ID);

			assertThat(response.stages()).extracting(RentalCheckpointResponse.Stage::step).containsExactly(
					RentalCheckpointResponse.Step.REGISTER, RentalCheckpointResponse.Step.HANDOVER,
					RentalCheckpointResponse.Step.RECEIVE, RentalCheckpointResponse.Step.RETURN);
			assertThat(response.stages().get(0).photos().get(0).imageUrl()).isEqualTo("https://img/clothes/front.jpg");
			assertThat(response.stages().get(1).takenBy()).isEqualTo(ActorType.OWNER);
			assertThat(response.stages().get(1).takenAt()).isEqualTo(NOW);
			assertThat(response.stages().get(2).photos()).isEmpty();
			assertThat(response.stages().get(2).takenAt()).isNull();
		}

		@Test
		void 거래_당사자가_아니면_볼_수_없다() {
			assertError(() -> service.getCheckpoints(99L, RENTAL_ID), RentalErrorCode.RENTAL_ACCESS_DENIED);
		}

	}

}
