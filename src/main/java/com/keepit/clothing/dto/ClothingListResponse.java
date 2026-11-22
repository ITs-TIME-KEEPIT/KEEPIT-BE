package com.keepit.clothing.dto;

import java.util.List;

/**
 * 옷 목록 한 묶음 (커서 방식, 무한 스크롤).
 * 다음 묶음은 nextCursor를 cursor 파라미터로 보내 받는다. 마지막 묶음이면 nextCursor는 null, hasNext는 false.
 * 전체 개수는 세지 않는다.
 */
public record ClothingListResponse(
		List<ClothingCardResponse> content,
		Long nextCursor,
		boolean hasNext
) {
}
