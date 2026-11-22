package com.keepit.image.controller;

import com.keepit.global.response.SuccessResponse;
import com.keepit.image.dto.ImageUploadResponse;
import com.keepit.image.exception.ImageErrorCode;
import com.keepit.image.exception.ImageException;
import com.keepit.image.service.ImageService;
import com.keepit.image.support.TemporaryUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 옷 사진 업로드 API (COM-003, REG-310 / API 명세 17번).
 * 로그인(JWT) 구현 전까지는 사용자 id를 TemporaryUser.ID로 고정한다.
 */
@Tag(name = "Image", description = "이미지 업로드")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

	private final ImageService imageService;

	@Operation(summary = "옷 사진 업로드 (COM-003, REG-310)",
			description = "JPEG·PNG만 받고, 파일명 확장자가 실제 형식과 같아야 한다(jpg·jpeg·png, 대소문자 무시). "
					+ "업로드된 이미지는 미연결 상태로 저장되고 옷 등록 때 연결된다. 응답의 시각은 모두 KST.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<SuccessResponse<ImageUploadResponse>> upload(
			// 누락을 Spring 기본 예외(500) 대신 IMAGE_400_1로 응답하려고 required = false로 받고 직접 확인한다
			@RequestPart(value = "file", required = false) MultipartFile file,
			@Parameter(description = "기기 촬영 시각(선택), 오프셋 포함 ISO-8601. 예: 2026-11-14T15:42:00+09:00")
			@RequestParam(value = "deviceTakenAt", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime deviceTakenAt) {
		// TODO: 10MB 초과(MaxUploadSizeExceededException)는 공통 예외 처리기가 없어 현재 500으로 응답한다(UT 이후 413 예정)
		if (file == null) {
			throw new ImageException(ImageErrorCode.IMAGE_FILE_REQUIRED);
		}
		ImageUploadResponse response = imageService.upload(TemporaryUser.ID, file, deviceTakenAt);
		SuccessResponse<ImageUploadResponse> body = SuccessResponse.created(response);
		return ResponseEntity.status(body.getHttpStatus()).body(body);
	}

}
