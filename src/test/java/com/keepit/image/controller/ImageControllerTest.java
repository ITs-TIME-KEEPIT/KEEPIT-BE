package com.keepit.image.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.global.exception.GlobalExceptionHandler;
import com.keepit.image.dto.ImageUploadResponse;
import com.keepit.image.exception.ImageErrorCode;
import com.keepit.image.exception.ImageException;
import com.keepit.image.service.ImageService;
import com.keepit.image.support.TemporaryUser;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ImageControllerTest {

	private static final String URL = "/api/v1/images";

	private final ImageService imageService = mock(ImageService.class);
	private MockMvc mockMvc;

	private final MockMultipartFile jpeg = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
			new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new ImageController(imageService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("업로드에 성공하면 201과 imageId·imageUrl을 돌려주고 임시 사용자로 저장한다")
	void 업로드_성공_201() throws Exception {
		when(imageService.upload(eq(TemporaryUser.ID), any(), isNull()))
				.thenReturn(new ImageUploadResponse(10L, "/local-images/clothes/abc.jpg"));

		mockMvc.perform(multipart(URL).file(jpeg))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS_201"))
				.andExpect(jsonPath("$.httpStatus").value(201))
				.andExpect(jsonPath("$.data.imageId").value(10))
				.andExpect(jsonPath("$.data.imageUrl").value("/local-images/clothes/abc.jpg"));
	}

	@Test
	@DisplayName("오프셋 포함 ISO-8601 촬영 시각을 받아 서비스에 그대로 넘긴다")
	void 촬영_시각_오프셋_포함_통과() throws Exception {
		OffsetDateTime takenAt = OffsetDateTime.of(2026, 11, 14, 15, 42, 0, 0, ZoneOffset.ofHours(9));
		when(imageService.upload(eq(TemporaryUser.ID), any(), eq(takenAt)))
				.thenReturn(new ImageUploadResponse(10L, "/local-images/clothes/abc.jpg"));

		mockMvc.perform(multipart(URL).file(jpeg).param("deviceTakenAt", "2026-11-14T15:42:00+09:00"))
				.andExpect(status().isCreated());

		verify(imageService).upload(eq(TemporaryUser.ID), any(), eq(takenAt));
	}

	@Test
	@DisplayName("촬영 시각 형식이 틀리면 서비스까지 가지 않고 GLOBAL_400_3")
	void 촬영_시각_형식_오류() throws Exception {
		mockMvc.perform(multipart(URL).file(jpeg).param("deviceTakenAt", "2026-11-14 15:42"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));

		verifyNoInteractions(imageService);
	}

	@Test
	@DisplayName("file 파트가 없으면 서비스까지 가지 않고 IMAGE_400_1")
	void 파일_누락() throws Exception {
		mockMvc.perform(multipart(URL).param("deviceTakenAt", "2026-11-14T15:42:00+09:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("IMAGE_400_1"));

		verifyNoInteractions(imageService);
	}

	@Test
	@DisplayName("서비스의 이미지 예외는 해당 코드와 상태로 응답한다")
	void 서비스_예외_응답() throws Exception {
		when(imageService.upload(any(), any(), any()))
				.thenThrow(new ImageException(ImageErrorCode.IMAGE_UPLOAD_FAILED));

		mockMvc.perform(multipart(URL).file(jpeg))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("IMAGE_500_1"));
	}

}
