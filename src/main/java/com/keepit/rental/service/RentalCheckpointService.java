package com.keepit.rental.service;

import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.global.s3.FileUploader;
import com.keepit.image.entity.Image;
import com.keepit.image.repository.ImageRepository;
import com.keepit.rental.dto.CheckpointPhotoUploadRequest;
import com.keepit.rental.dto.RentalCheckpointResponse;
import com.keepit.rental.dto.RentalCheckpointResponse.Step;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalPhoto;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import com.keepit.rental.repository.RentalPhotoRepository;
import com.keepit.rental.repository.RentalRepository;
import com.keepit.rental.repository.RentalSupportRepository;
import com.keepit.user.entity.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 체크포인트 사진 등록·비교 조회 (TRD-510·520·530·550).
 * 사진 규칙은 {@link CheckpointPhotoPolicy}에 두고, 여기서는 권한·상태 확인과 저장, 조회 응답 조립을 한다.
 * "사진이 없으면 다음 단계로 못 간다"는 검사는 상태를 바꾸는 {@link RentalService} 쪽에 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RentalCheckpointService {

	private final RentalRepository rentalRepository;
	private final RentalSupportRepository supportRepository;
	private final RentalPhotoRepository rentalPhotoRepository;
	private final ImageRepository imageRepository;
	private final ClothingPhotoRepository clothingPhotoRepository;
	private final FileUploader fileUploader;
	private final Clock clock;

	/**
	 * 한 단계의 사진을 등록한다. 같은 단계에 다시 올리면 기존 사진을 지우고 새 사진으로 바꾼다
	 * (사진 등록은 됐는데 다음 단계 요청이 실패해 다시 시도하는 경우를 위해).
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void uploadPhotos(Long userId, Long rentalId, RentalPhotoStage stage,
			List<CheckpointPhotoUploadRequest.Photo> photos) {
		// 단계 규칙: 반납 세탁 사진(RETURN_WASHED)처럼 따로 올리지 않는 단계는 여기서 거른다
		ActorType photographer = CheckpointPhotoPolicy.photographerOf(stage);

		// 상태 전이(전달·수령·반납)와 같은 순서로 옷 행을 잠근다. 사진 교체와 다음 단계 처리가 동시에 들어와도 한쪽씩 처리된다
		Rental rental = lockClothingAndGetRental(rentalId);

		// 이 단계를 찍는 사람인지, 지금 이 단계 사진을 올릴 상태인지 확인한다
		boolean isPhotographer = photographer == ActorType.OWNER ? rental.isOwnedBy(userId) : rental.isBorrowedBy(userId);
		if (!isPhotographer) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}
		if (rental.getStatus() != CheckpointPhotoPolicy.statusFor(stage)) {
			throw new RentalException(RentalErrorCode.INVALID_RENTAL_STATUS);
		}

		// 부위 검사: 전면·후면·라벨 필수, 디테일 선택, 같은 부위 중복 불가
		CheckpointPhotoPolicy.validateParts(photos.stream().map(CheckpointPhotoUploadRequest.Photo::photoPart).toList());

		// 아래 delete·link는 영속성 컨텍스트를 비우므로, 저장에 쓸 찍은 사람을 미리 꺼내 둔다
		User uploader = photographer == ActorType.OWNER ? rental.getOwner() : rental.getBorrower();

		// 교체: 이 단계에 이미 올린 사진을 먼저 지운다
		rentalPhotoRepository.deleteStage(rentalId, stage);

		// 본인이 올린, 아직 아무 데도 안 쓰인 이미지만 이 거래에 연결한다
		LocalDateTime now = LocalDateTime.now(clock);
		Map<Long, Image> images = linkImages(userId, photos, now);

		// 요청 순서대로 rental_photos에 저장한다. 촬영 시각은 서버가 이미지를 받은 시각을 쓴다
		List<RentalPhoto> saved = new ArrayList<>();
		for (int i = 0; i < photos.size(); i++) {
			CheckpointPhotoUploadRequest.Photo photo = photos.get(i);
			Image image = images.get(photo.imageId());
			saved.add(RentalPhoto.builder()
					.rental(rental)
					.uploader(uploader)
					.stage(stage)
					.photoPart(photo.photoPart())
					.imageKey(image.getImageKey())
					.sortOrder(i)
					.serverReceivedAt(image.getServerReceivedAt())
					.deviceTakenAt(image.getDeviceTakenAt())
					.build());
		}
		rentalPhotoRepository.saveAll(saved);
	}

	/**
	 * 4단계 사진 비교 (TRD-550). 거래 당사자만 볼 수 있다.
	 * 1단계(등록 시)는 옷 등록 사진, 2~4단계는 이 거래의 체크포인트 사진이다.
	 */
	public RentalCheckpointResponse getCheckpoints(Long userId, Long rentalId) {
		// 거래 당사자(주인·대여자)인지 확인한다
		Rental rental = rentalRepository.findById(rentalId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.RENTAL_NOT_FOUND));
		if (!rental.isOwnedBy(userId) && !rental.isBorrowedBy(userId)) {
			throw new RentalException(RentalErrorCode.RENTAL_ACCESS_DENIED);
		}

		// 1단계: 옷 등록 사진 (등록자 촬영)
		List<RentalCheckpointResponse.Photo> registerPhotos = clothingPhotoRepository
				.findAllOfClothing(rental.getClothing().getId()).stream()
				.map(this::toPhoto)
				.toList();

		// 2~4단계: 이 거래의 사진을 단계별로 묶는다
		Map<RentalPhotoStage, List<RentalPhoto>> byStage = rentalPhotoRepository.findAllOfRental(rentalId).stream()
				.collect(Collectors.groupingBy(RentalPhoto::getStage));

		List<RentalCheckpointResponse.Stage> stages = List.of(
				stage(Step.REGISTER, ActorType.OWNER, registerPhotos),
				stage(Step.HANDOVER, ActorType.OWNER, toPhotos(byStage.get(RentalPhotoStage.HANDOVER))),
				stage(Step.RECEIVE, ActorType.BORROWER, toPhotos(byStage.get(RentalPhotoStage.RECEIVE))),
				stage(Step.RETURN, ActorType.BORROWER, toPhotos(byStage.get(RentalPhotoStage.RETURN))));
		return new RentalCheckpointResponse(rentalId, stages);
	}

	/**
	 * 본인이 올린 미연결 이미지만 연결하고 이미지 정보를 돌려준다.
	 * 연결된 수가 요청 수와 다르면 없는·남의·이미 쓰인·중복 id가 섞인 것이라 전체를 막는다.
	 */
	private Map<Long, Image> linkImages(Long userId, List<CheckpointPhotoUploadRequest.Photo> photos,
			LocalDateTime now) {
		List<Long> imageIds = photos.stream().map(CheckpointPhotoUploadRequest.Photo::imageId).toList();
		int linked = imageRepository.link(imageIds, userId, now);
		if (linked != imageIds.size()) {
			throw new RentalException(RentalErrorCode.CHECKPOINT_IMAGE_UNUSABLE);
		}
		return imageRepository.findAllById(imageIds).stream()
				.collect(Collectors.toMap(Image::getId, Function.identity()));
	}

	/** 한 단계 응답. 촬영 시각은 그 단계 사진 중 가장 이른 서버 수신 시각, 사진이 없으면 null. */
	private static RentalCheckpointResponse.Stage stage(Step step, ActorType takenBy,
			List<RentalCheckpointResponse.Photo> photos) {
		LocalDateTime takenAt = photos.stream()
				.map(RentalCheckpointResponse.Photo::serverReceivedAt)
				.min(Comparator.naturalOrder())
				.orElse(null);
		return new RentalCheckpointResponse.Stage(step, takenBy, takenAt, photos);
	}

	private List<RentalCheckpointResponse.Photo> toPhotos(List<RentalPhoto> photos) {
		if (photos == null) {
			return List.of();
		}
		return photos.stream()
				.map(p -> new RentalCheckpointResponse.Photo(p.getPhotoPart(), fileUploader.getUrl(p.getImageKey()),
						p.getServerReceivedAt()))
				.toList();
	}

	private RentalCheckpointResponse.Photo toPhoto(ClothingPhoto photo) {
		return new RentalCheckpointResponse.Photo(photo.getPhotoPart(), fileUploader.getUrl(photo.getImageKey()),
				photo.getServerReceivedAt());
	}

	/** RentalService와 같은 잠금 순서: 옷 id 조회 → 옷 행 잠금 → 잠근 뒤 신청을 읽는다. 이유는 RentalService 주석 참고. */
	private Rental lockClothingAndGetRental(Long rentalId) {
		Long clothingId = rentalRepository.findClothingIdById(rentalId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.RENTAL_NOT_FOUND));
		supportRepository.findClothingForUpdate(clothingId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.CLOTHING_NOT_FOUND));
		return rentalRepository.findById(rentalId)
				.orElseThrow(() -> new RentalException(RentalErrorCode.RENTAL_NOT_FOUND));
	}

}
