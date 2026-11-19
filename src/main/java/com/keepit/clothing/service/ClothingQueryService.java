package com.keepit.clothing.service;

import com.keepit.clothing.dto.ClothingCardResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.dto.RegionResponse;
import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.clothing.repository.ClothingRepository;
import com.keepit.global.s3.FileUploader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
