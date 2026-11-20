package com.keepit.clothing.service;

import com.keepit.clothing.dto.ClothingCardResponse;
import com.keepit.clothing.dto.ClothingDetailResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.dto.RegionResponse;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.clothing.repository.ClothingColorRepository;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.clothing.repository.ClothingRepository;
import com.keepit.clothing.repository.ClothingUnavailablePeriodRepository;
import com.keepit.global.s3.FileUploader;
import com.keepit.user.entity.User;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 옷 목록·상세 조회 (API 13·14번). 이미지는 key가 아니라 FileUploader.getUrl로 만든 URL로 응답한다(응답마다 새로 발급).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClothingQueryService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 50;

	/** 대표 사진 부위 (임시: 전면 사진 중 sort_order가 가장 작은 것, 프론트 합의 필요). */
	private static final PhotoPart THUMBNAIL_PART = PhotoPart.FRONT;

	private final ClothingRepository clothingRepository;
	private final ClothingPhotoRepository photoRepository;
	private final ClothingColorRepository colorRepository;
	private final ClothingUnavailablePeriodRepository unavailablePeriodRepository;
	private final FileUploader fileUploader;

	/**
	 * 대여 가능(AVAILABLE)·미삭제 옷을 최신 등록순으로 준다. 쿼리는 옷+지역 1번, 대표 사진 1번.
	 */
	public ClothingListResponse getList(ClothingCategory category, int page, int size) {
		Slice<Clothing> slice = clothingRepository.findList(ClothingStatus.AVAILABLE, category,
				PageRequest.of(page, size));
		Map<Long, String> thumbnails = thumbnailUrls(slice.getContent());
		List<ClothingCardResponse> content = slice.getContent().stream()
				.map(clothing -> toCard(clothing, thumbnails.get(clothing.getId())))
				.toList();
		return new ClothingListResponse(content, page, size, slice.hasNext());
	}

	/**
	 * 옷 상세. 대여 중지 옷도 주고, 없거나 삭제된 옷이면 CLOTHING_404_1.
	 * 쿼리는 옷+지역+등록자 1번, 사진·컬러·대여 불가 기간 각 1번.
	 */
	public ClothingDetailResponse getDetail(Long clothesId) {
		Clothing clothing = clothingRepository.findDetail(clothesId)
				.orElseThrow(() -> new ClothingException(ClothingErrorCode.CLOTHING_NOT_FOUND));
		Long id = clothing.getId();

		List<ClothingDetailResponse.Photo> photos = photoRepository.findAllOfClothing(id).stream()
				.map(photo -> new ClothingDetailResponse.Photo(photo.getPhotoPart(), photo.getSortOrder(),
						fileUploader.getUrl(photo.getImageKey()), photo.getServerReceivedAt()))
				.toList();
		List<Color> colors = colorRepository.findAllOfClothing(id).stream()
				.map(ClothingColor::getColor)
				.toList();
		List<ClothingDetailResponse.UnavailablePeriod> periods = unavailablePeriodRepository.findAllOfClothing(id)
				.stream()
				.map(period -> new ClothingDetailResponse.UnavailablePeriod(period.getStartDate(), period.getEndDate()))
				.toList();

		return new ClothingDetailResponse(
				id,
				clothing.getStatus(),
				clothing.getTitle(),
				clothing.getDescription(),
				clothing.getCategory(),
				clothing.getSize(),
				new ClothingDetailResponse.Measurement(clothing.getTotalLength(), clothing.getShoulderWidth(),
						clothing.getChestWidth(), clothing.getSleeveLength(), clothing.getWaistWidth(),
						clothing.getHipWidth(), clothing.getThighWidth(), clothing.getHemWidth()),
				colors,
				clothing.isHasDefect(),
				clothing.getDefectDescription(),
				RegionResponse.from(clothing.getRegion()),
				clothing.getRentalFee(),
				clothing.isDeliveryAvailable(),
				clothing.getDeliveryFee(),
				clothing.getLaundryBy(),
				clothing.getLaundryMethod(),
				periods,
				photos,
				toOwner(clothing.getOwner()),
				clothing.getCreatedAt());
	}

	private ClothingDetailResponse.Owner toOwner(User owner) {
		return new ClothingDetailResponse.Owner(owner.getId(), owner.getNickname(), profileImageUrl(owner));
	}

	/**
	 * TODO: 프로필 사진 URL 생성 방식은 1번 가입·수정 API와 공용 업로더가 확정된 뒤 맞춘다.
	 * 지금은 profile_image_key를 옷 사진과 같은 저장소 key로 보고 FileUploader.getUrl로 바꾼다(임시).
	 * 1번이 카카오 프로필 이미지 주소를 그대로 저장하기로 하면 변환하지 않고 그대로 내려야 한다.
	 */
	private String profileImageUrl(User owner) {
		String key = owner.getProfileImageKey();
		return StringUtils.hasText(key) ? fileUploader.getUrl(key) : null;
	}

	private Map<Long, String> thumbnailUrls(List<Clothing> clothes) {
		if (clothes.isEmpty()) {
			return Map.of();
		}
		List<Long> clothingIds = clothes.stream().map(Clothing::getId).toList();
		Map<Long, String> urls = new HashMap<>();
		// 옷마다 sort_order, id 순으로 정렬돼 오므로 처음 나온 사진이 대표 사진이다
		for (ClothingPhoto photo : photoRepository.findByClothingIdsAndPart(clothingIds, THUMBNAIL_PART)) {
			urls.putIfAbsent(photo.getClothing().getId(), photo.getImageKey());
		}
		urls.replaceAll((clothingId, key) -> fileUploader.getUrl(key));
		return urls;
	}

	private static ClothingCardResponse toCard(Clothing clothing, String thumbnailUrl) {
		return new ClothingCardResponse(
				clothing.getId(),
				thumbnailUrl,
				clothing.getTitle(),
				clothing.getCategory(),
				clothing.getSize(),
				RegionResponse.from(clothing.getRegion()),
				clothing.getRentalFee(),
				clothing.getLaundryBy(),
				clothing.isDeliveryAvailable(),
				clothing.getCreatedAt());
	}

}
