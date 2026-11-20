package com.keepit.clothing.service;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingPhotoRequest;
import com.keepit.clothing.dto.MeasurementRequest;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.clothing.repository.ClothingColorRepository;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.clothing.repository.ClothingRepository;
import com.keepit.clothing.repository.ClothingUnavailablePeriodRepository;
import com.keepit.image.entity.Image;
import com.keepit.image.repository.ImageRepository;
import com.keepit.region.entity.Region;
import com.keepit.region.repository.RegionRepository;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 옷 등록 (API 18번).
 * 검증 → 이미지 연결 → 지역 확인 → 옷·컬러·대여 불가 기간·사진 저장을 한 트랜잭션에서 처리한다.
 * 중간에 실패하면 이미지 연결까지 모두 롤백된다.
 */
@Service
@RequiredArgsConstructor
public class ClothingService {

	private final ClothingRepository clothingRepository;
	private final ClothingColorRepository colorRepository;
	private final ClothingPhotoRepository photoRepository;
	private final ClothingUnavailablePeriodRepository unavailablePeriodRepository;
	private final RegionRepository regionRepository;
	private final ImageRepository imageRepository;
	private final EntityManager entityManager;
	private final Clock clock;

	@Transactional
	public Long register(Long ownerId, ClothingCreateRequest request) {
		LocalDateTime now = LocalDateTime.now(clock);
		ClothingCreateValidator.validate(request, now.toLocalDate());

		// 이미지 연결(bulk update)은 영속성 컨텍스트를 비우므로 다른 엔티티 조회·저장보다 먼저 한다
		List<ClothingPhotoRequest> photos = ClothingCreateValidator.photosToLink(request);
		Map<Long, Image> images = linkImages(ownerId, photos, now);
		Region region = findSelectableRegion(request.regionId());

		Clothing clothing = clothingRepository.save(toClothing(ownerId, region, request));
		colorRepository.saveAll(request.colors().stream()
				.map(color -> new ClothingColor(clothing, color))
				.toList());
		unavailablePeriodRepository.saveAll(request.unavailablePeriodsOrEmpty().stream()
				.map(period -> ClothingUnavailablePeriod.builder()
						.clothing(clothing)
						.startDate(period.startDate())
						.endDate(period.endDate())
						.build())
				.toList());
		photoRepository.saveAll(photos.stream()
				.map(photo -> toClothingPhoto(clothing, photo, images.get(photo.imageId())))
				.toList());
		return clothing.getId();
	}

	private Region findSelectableRegion(Integer regionId) {
		return regionRepository.findById(regionId)
				.filter(Region::isOpen)
				.orElseThrow(() -> new ClothingException(ClothingErrorCode.REGION_NOT_SELECTABLE));
	}

	/**
	 * 본인이 올린 미연결 이미지만 연결한다. 하나라도 없거나 남의 것이거나 이미 연결됐으면 CLOTHING_400_11.
	 */
	private Map<Long, Image> linkImages(Long ownerId, List<ClothingPhotoRequest> photos, LocalDateTime now) {
		List<Long> imageIds = photos.stream().map(ClothingPhotoRequest::imageId).toList();
		int linked = imageRepository.link(imageIds, ownerId, now);
		if (linked != imageIds.size()) {
			throw new ClothingException(ClothingErrorCode.UNUSABLE_IMAGE);
		}
		return imageRepository.findAllById(imageIds).stream()
				.collect(Collectors.toMap(Image::getId, Function.identity()));
	}

	private Clothing toClothing(Long ownerId, Region region, ClothingCreateRequest request) {
		MeasurementRequest measurement = ClothingCreateValidator.allowedMeasurement(request.category(),
				request.measurement());
		boolean hasDefect = request.hasDefect();
		boolean deliveryAvailable = request.deliveryAvailable();
		return Clothing.builder()
				.owner(entityManager.getReference(User.class, ownerId))
				.region(region)
				.title(request.title())
				.description(request.description())
				.category(request.category())
				.size(request.category() == ClothingCategory.ACC ? ClothingSize.FREE : request.size())
				.totalLength(measurement.totalLength())
				.shoulderWidth(measurement.shoulderWidth())
				.chestWidth(measurement.chestWidth())
				.sleeveLength(measurement.sleeveLength())
				.waistWidth(measurement.waistWidth())
				.hipWidth(measurement.hipWidth())
				.thighWidth(measurement.thighWidth())
				.hemWidth(measurement.hemWidth())
				.hasDefect(hasDefect)
				.defectDescription(hasDefect ? request.defectDescription() : null)
				.rentalFee(request.rentalFee())
				.deliveryAvailable(deliveryAvailable)
				.deliveryFee(deliveryAvailable ? request.deliveryFee() : null)
				.laundryBy(request.laundryBy())
				.laundryMethod(request.laundryMethod() == null ? LaundryMethod.DRY_CLEAN : request.laundryMethod())
				.build();
	}

	/** 업로드 때 기록한 key와 촬영·수신 시각을 옷 사진으로 복사한다. */
	private static ClothingPhoto toClothingPhoto(Clothing clothing, ClothingPhotoRequest photo, Image image) {
		return ClothingPhoto.builder()
				.clothing(clothing)
				.photoPart(photo.photoPart())
				.imageKey(image.getImageKey())
				.sortOrder(photo.sortOrder())
				.serverReceivedAt(image.getServerReceivedAt())
				.deviceTakenAt(image.getDeviceTakenAt())
				.build();
	}

}
