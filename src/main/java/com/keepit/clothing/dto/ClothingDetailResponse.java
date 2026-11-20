package com.keepit.clothing.dto;

import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 옷 상세 (BRW-210). 대여 중지(PAUSED) 옷도 status와 함께 준다.
 * 기기 촬영 시각과 등록자 휴대폰 번호는 내려주지 않는다.
 * 대여 불가 기간은 등록자가 막아 둔 원본 구간이고, 계산된 대여 가능 날짜는 대여(3번) availability API가 맡는다.
 */
public record ClothingDetailResponse(
		Long clothesId,
		ClothingStatus status,
		String title,
		String description,
		ClothingCategory category,
		ClothingSize size,
		Measurement measurement,
		List<Color> colors,
		boolean hasDefect,
		String defectDescription,
		RegionResponse region,
		int rentalFee,
		boolean deliveryAvailable,
		Integer deliveryFee,
		LaundryBy laundryBy,
		LaundryMethod laundryMethod,
		List<UnavailablePeriod> unavailablePeriods,
		List<Photo> photos,
		Owner owner,
		LocalDateTime createdAt
) {

	/** 실측(cm). 입력하지 않았거나 카테고리에 없는 항목은 null. */
	public record Measurement(
			BigDecimal totalLength,
			BigDecimal shoulderWidth,
			BigDecimal chestWidth,
			BigDecimal sleeveLength,
			BigDecimal waistWidth,
			BigDecimal hipWidth,
			BigDecimal thighWidth,
			BigDecimal hemWidth
	) {
	}

	/** 사진은 sortOrder, 등록 순으로 정렬돼 있다. serverReceivedAt은 서버가 사진을 받은 KST 시각. */
	public record Photo(
			PhotoPart photoPart,
			int sortOrder,
			String imageUrl,
			LocalDateTime serverReceivedAt
	) {
	}

	public record UnavailablePeriod(
			LocalDate startDate,
			LocalDate endDate
	) {
	}

	/** 등록자. profileImageUrl은 없으면 null. */
	public record Owner(
			Long userId,
			String nickname,
			String profileImageUrl
	) {
	}

}
