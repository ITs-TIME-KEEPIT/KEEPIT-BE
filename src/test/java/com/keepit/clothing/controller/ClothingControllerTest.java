package com.keepit.clothing.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingDetailResponse;
import com.keepit.clothing.dto.ClothingListResponse;
import com.keepit.clothing.dto.RegionResponse;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.ClothingStatus;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.LaundryMethod;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import com.keepit.clothing.service.ClothingQueryService;
import com.keepit.clothing.service.ClothingService;
import com.keepit.global.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ClothingControllerTest {

	private static final String URL = "/api/v1/clothes";

	/** 토큰에서 꺼낸 회원 id. 고정값 1이 아닌 값으로 토큰 사용자가 쓰이는지 확인한다. */
	private static final Long LOGIN_USER_ID = 7L;

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
	private final ClothingQueryService clothingQueryService = mock(ClothingQueryService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		// @AuthenticationPrincipal을 풀 수 있게 해석기를 등록한다
		mockMvc = MockMvcBuilders.standaloneSetup(new ClothingController(clothingService, clothingQueryService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
				.build();
		// JWT 필터가 하는 일: principal에 회원 id를 넣는다
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(LOGIN_USER_ID, null, List.of()));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private org.springframework.test.web.servlet.ResultActions postBody(String body) throws Exception {
		return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	@DisplayName("등록에 성공하면 201과 clothesId를 돌려주고 토큰의 회원으로 등록한다")
	void 등록_성공_201() throws Exception {
		when(clothingService.register(eq(LOGIN_USER_ID), any(ClothingCreateRequest.class))).thenReturn(42L);

		postBody(VALID_BODY)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS_201"))
				.andExpect(jsonPath("$.httpStatus").value(201))
				.andExpect(jsonPath("$.data.clothesId").value(42));

		verify(clothingService).register(eq(LOGIN_USER_ID), any(ClothingCreateRequest.class));
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

	@Test
	@DisplayName("목록은 커서 없이 기본 size 20으로 조회하고 200과 nextCursor·hasNext를 돌려준다")
	void 목록_기본값() throws Exception {
		when(clothingQueryService.getList(null, null, 20))
				.thenReturn(new ClothingListResponse(List.of(), null, false));

		mockMvc.perform(get(URL))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.code").value("SUCCESS_200"))
				.andExpect(jsonPath("$.data.content").isArray())
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.page").doesNotHaveJsonPath());
	}

	@Test
	@DisplayName("카테고리·cursor·size를 그대로 서비스에 넘긴다 (size 최대 50)")
	void 목록_파라미터_전달() throws Exception {
		when(clothingQueryService.getList(ClothingCategory.DRESS, 37L, 50))
				.thenReturn(new ClothingListResponse(List.of(), 12L, true));

		mockMvc.perform(get(URL).param("category", "DRESS").param("cursor", "37").param("size", "50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nextCursor").value(12))
				.andExpect(jsonPath("$.data.hasNext").value(true));

		verify(clothingQueryService).getList(ClothingCategory.DRESS, 37L, 50);
	}

	@ParameterizedTest(name = "{0}={1}")
	@CsvSource({"size, 0", "size, 51", "cursor, 0", "cursor, -1", "cursor, abc"})
	@DisplayName("size·cursor가 범위를 벗어나거나 숫자가 아니면 서비스까지 가지 않고 GLOBAL_400_3")
	void 목록_파라미터_범위_오류(String name, String value) throws Exception {
		mockMvc.perform(get(URL).param(name, value))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));

		verifyNoInteractions(clothingQueryService);
	}

	@Test
	@DisplayName("상세는 200과 함께 시각을 ISO 형식(오프셋 없음)으로 준다")
	void 상세_조회() throws Exception {
		ClothingDetailResponse detail = new ClothingDetailResponse(42L, ClothingStatus.PAUSED, "원피스", null,
				ClothingCategory.DRESS, ClothingSize.M,
				new ClothingDetailResponse.Measurement(new BigDecimal("105.0"), null, null, null, null, null, null, null),
				List.of(Color.NAVY), false, null, new RegionResponse(14, "서울", "마포구"), 25000, false, null,
				LaundryBy.BORROWER, LaundryMethod.DRY_CLEAN,
				List.of(new ClothingDetailResponse.UnavailablePeriod(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 26))),
				List.of(new ClothingDetailResponse.Photo(PhotoPart.FRONT, 0, "/local-images/clothes/a.jpg",
						LocalDateTime.of(2026, 11, 19, 21, 40, 3, 512004000))),
				new ClothingDetailResponse.Owner(1L, "옷장주인", null),
				LocalDateTime.of(2026, 11, 19, 21, 49, 22));
		when(clothingQueryService.getDetail(42L)).thenReturn(detail);

		mockMvc.perform(get(URL + "/42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.clothesId").value(42))
				.andExpect(jsonPath("$.data.status").value("PAUSED"))
				.andExpect(jsonPath("$.data.region.sigungu").value("마포구"))
				.andExpect(jsonPath("$.data.measurement.totalLength").value(105.0))
				.andExpect(jsonPath("$.data.measurement.hemWidth").doesNotExist())
				.andExpect(jsonPath("$.data.unavailablePeriods[0].startDate").value("2026-12-24"))
				.andExpect(jsonPath("$.data.photos[0].serverReceivedAt").value("2026-11-19T21:40:03.512004"))
				.andExpect(jsonPath("$.data.owner.nickname").value("옷장주인"))
				.andExpect(jsonPath("$.data.createdAt").value("2026-11-19T21:49:22"));
	}

	@Test
	@DisplayName("없거나 삭제된 옷이면 404와 CLOTHING_404_1")
	void 상세_없음() throws Exception {
		when(clothingQueryService.getDetail(99L))
				.thenThrow(new ClothingException(ClothingErrorCode.CLOTHING_NOT_FOUND));

		mockMvc.perform(get(URL + "/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CLOTHING_404_1"))
				.andExpect(jsonPath("$.message").value("옷을 찾을 수 없습니다."));
	}

	@Test
	@DisplayName("옷 id가 숫자가 아니면 GLOBAL_400_3")
	void 상세_잘못된_id() throws Exception {
		mockMvc.perform(get(URL + "/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));

		verifyNoInteractions(clothingQueryService);
	}

	@Test
	@DisplayName("없는 카테고리 값이면 GLOBAL_400_3")
	void 목록_잘못된_카테고리() throws Exception {
		mockMvc.perform(get(URL).param("category", "SHOES"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));

		verifyNoInteractions(clothingQueryService);
	}

}
