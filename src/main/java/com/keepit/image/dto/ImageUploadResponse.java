package com.keepit.image.dto;

/**
 * 이미지 업로드 결과. imageId는 옷 등록 요청의 photos[].imageId로 쓰고, imageUrl은 화면 미리보기용이다.
 */
public record ImageUploadResponse(
		Long imageId,
		String imageUrl
) {
}
