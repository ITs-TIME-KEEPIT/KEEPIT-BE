package com.keepit.clothing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.clothing.service.ClothingService;
import com.keepit.global.exception.GlobalExceptionHandler;
import com.keepit.image.support.TemporaryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ClothingControllerTest {

	private static final String URL = "/api/v1/clothes";

	private static final String VALID_BODY = """
			{
			  "title": "하객룩 네이비 원피스",
			  "description": "한 번 입었어요",
			  "category": "DRESS",
			  "size": "M",
			  "colors": ["NAVY"],
			  "measurement": {"totalLength": 105.0, "shoulderWidth": 36.5, "chestWidth": 44.0, "waistWidth": 34.0},
			  "hasDefect": true,
			  "defectDescription": "밑단 안쪽 올 풀림",
			  "regionId": 14,
			  "rentalFee": 25000,
			  "deliveryAvailable": true,
			  "deliveryFee": 3500,
			  "laundryBy": "BORROWER",
			  "laundryMethod": "DRY_CLEAN",
			  "unavailablePeriods": [{"startDate": "2026-12-24", "endDate": "2026-12-26"}],
			  "photos": [
			    {"imageId": 101, "photoPart": "FRONT", "sortOrder": 0},
			    {"imageId": 102, "photoPart": "BACK", "sortOrder": 1},
			    {"imageId": 103, "photoPart": "LABEL", "sortOrder": 2},
			    {"imageId": 105, "photoPart": "DEFECT", "sortOrder": 3}
			  ]
			}
			""";

	private final ClothingService clothingService = mock(ClothingService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ClothingController(clothingService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	private org.springframework.test.web.servlet.ResultActions postBody(String body) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	@DisplayName("등록에 성공하면 201과 clothesId를 돌려주고 임시 사용자로 등록한다")
	void 등록_성공_201() throws Exception {
		when(clothingService.register(eq(TemporaryUser.ID), any(ClothingCreateRequest.class))).thenReturn(42L);

		postBody(VALID_BODY)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS_201"))
				.andExpect(jsonPath("$.httpStatus").value(201))
				.andExpect(jsonPath("$.data.clothesId").value(42));

		verify(clothingService).register(eq(TemporaryUser.ID), any(ClothingCreateRequest.class));
	}

	@Test
	@DisplayName("제목이 한 글자면 서비스까지 가지 않고 GLOBAL_400_2")
	void 제목_길이() throws Exception {
		postBody(VALID_BODY.replace("\"하객룩 네이비 원피스\"", "\"옷\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"))
				.andExpect(jsonPath("$.message").value("제목 길이가 올바르지 않습니다."));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("컬러가 세 개면 GLOBAL_400_2")
	void 컬러_개수() throws Exception {
		postBody(VALID_BODY.replace("[\"NAVY\"]", "[\"NAVY\", \"BLACK\", \"WHITE\"]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"))
				.andExpect(jsonPath("$.message").value("컬러 개수가 올바르지 않습니다."));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("실측 값이 0.1보다 작거나 소수 둘째 자리까지 있으면 GLOBAL_400_2")
	void 실측_범위() throws Exception {
		postBody(VALID_BODY.replace("\"totalLength\": 105.0", "\"totalLength\": 0.0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));
		postBody(VALID_BODY.replace("\"totalLength\": 105.0", "\"totalLength\": 105.25"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("대여료가 0원이면 GLOBAL_400_2")
	void 대여료_최소값() throws Exception {
		postBody(VALID_BODY.replace("\"rentalFee\": 25000", "\"rentalFee\": 0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("사진 순서가 없으면 GLOBAL_400_2")
	void 사진_순서_필수() throws Exception {
		postBody(VALID_BODY.replace("{\"imageId\": 101, \"photoPart\": \"FRONT\", \"sortOrder\": 0}",
						"{\"imageId\": 101, \"photoPart\": \"FRONT\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("없는 카테고리 값이면 GLOBAL_400_2")
	void 잘못된_enum() throws Exception {
		postBody(VALID_BODY.replace("\"DRESS\"", "\"SHOES\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));

		verifyNoInteractions(clothingService);
	}

	@Test
	@DisplayName("서비스의 옷 예외는 해당 코드와 메시지로 응답한다")
	void 서비스_예외_응답() throws Exception {
		when(clothingService.register(any(), any()))
				.thenThrow(new ClothingException(ClothingErrorCode.REQUIRED_PHOTO_PART_MISSING));

		postBody(VALID_BODY)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("CLOTHING_400_4"))
				.andExpect(jsonPath("$.message").value("전면·후면·라벨 사진이 모두 필요합니다."));
	}

}
