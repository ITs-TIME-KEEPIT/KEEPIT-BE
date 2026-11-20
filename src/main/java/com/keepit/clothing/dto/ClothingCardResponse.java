package com.keepit.clothing.dto;

import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.LaundryBy;
import java.time.LocalDateTime;

/**
 * 홈 목록 카드 (BRW-201).
 * thumbnailUrl은 대표 사진(임시: 전면 사진 중 sort_order가 가장 작은 것)이고, 없으면 null.
 * createdAt은 KST 시각 그대로이며 'n일 전' 표시는 프론트가 한다.
 */
public record ClothingCardResponse(
		Long clothesId,
		String thumbnailUrl,
		String title,
		ClothingCategory category,
		ClothingSize size,
		RegionResponse region,
		int rentalFee,
		LaundryBy laundryBy,
		boolean deliveryAvailable,
		LocalDateTime createdAt
) {
}
