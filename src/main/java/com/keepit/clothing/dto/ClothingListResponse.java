package com.keepit.clothing.dto;

import java.util.List;

/**
 * 옷 목록 페이지 (offset 방식, page는 0부터). 전체 개수는 세지 않고 다음 페이지가 있는지만 알려 준다.
 */
public record ClothingListResponse(
		List<ClothingCardResponse> content,
		int page,
		int size,
		boolean hasNext
) {
}
