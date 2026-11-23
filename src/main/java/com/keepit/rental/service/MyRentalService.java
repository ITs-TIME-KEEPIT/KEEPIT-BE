package com.keepit.rental.service;

import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.repository.ClothingPhotoRepository;
import com.keepit.global.s3.FileUploader;
import com.keepit.rental.dto.MyRentalResponse;
import com.keepit.rental.dto.MyRentalRole;
import com.keepit.rental.entity.Rental;
import com.keepit.rental.repository.RentalRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 내 거래 내역 (MYP-601). 빌린 옷 / 빌려준 옷 탭별로 모든 상태의 거래를 최신 신청순으로 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyRentalService {

	private final RentalRepository rentalRepository;
	private final ClothingPhotoRepository clothingPhotoRepository;
	private final FileUploader fileUploader;

	public List<MyRentalResponse> getMyRentals(Long userId, MyRentalRole role) {
		// 탭에 맞는 거래를 옷·지역·상대와 함께 한 번에 가져온다
		List<Rental> rentals = role == MyRentalRole.BORROWER
				? rentalRepository.findAllBorrowedBy(userId)
				: rentalRepository.findAllLentBy(userId);
		if (rentals.isEmpty()) {
			return List.of();
		}

		// 옷마다 대표 사진(전면 첫 장)을 한 번의 쿼리로 모은다(옷 목록과 같은 방식)
		Map<Long, String> thumbnails = thumbnailUrls(rentals);

		return rentals.stream()
				.map(rental -> MyRentalResponse.of(rental, role, thumbnails.get(rental.getClothing().getId())))
				.toList();
	}

	/** 옷 id → 대표 사진 URL. 전면 사진이 sortOrder 순으로 오므로 옷마다 첫 장만 쓴다. */
	private Map<Long, String> thumbnailUrls(List<Rental> rentals) {
		List<Long> clothingIds = rentals.stream().map(r -> r.getClothing().getId()).distinct().toList();
		Map<Long, String> keys = new HashMap<>();
		for (ClothingPhoto photo : clothingPhotoRepository.findByClothingIdsAndPart(clothingIds, PhotoPart.FRONT)) {
			keys.putIfAbsent(photo.getClothing().getId(), photo.getImageKey());
		}
		keys.replaceAll((clothingId, key) -> fileUploader.getUrl(key));
		return keys;
	}

}
