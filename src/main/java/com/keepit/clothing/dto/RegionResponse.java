package com.keepit.clothing.dto;

import com.keepit.region.entity.Region;

/**
 * 거래 지역. 화면의 '서울 마포구' 같은 표시는 프론트가 sido와 sigungu를 이어 붙여 만든다.
 */
public record RegionResponse(
		Integer regionId,
		String sido,
		String sigungu
) {

	public static RegionResponse from(Region region) {
		return new RegionResponse(region.getId(), region.getSido(), region.getSigungu());
	}

}
