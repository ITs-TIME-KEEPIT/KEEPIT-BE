package com.keepit.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.global.config.SecurityConfig;
import com.keepit.user.controller.ProfileRegistrationController;
import com.keepit.user.dto.ProfileResponse;
import com.keepit.user.service.ProfileRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * [로그인 임시] SecurityConfig와 TemporaryUserFilter를 함께 띄워, Authorization의 1·2가
 * 컨트롤러의 {@code @AuthenticationPrincipal Long userId}까지 전달되는지와 401 처리를 검증한다.
 */
@WebMvcTest(ProfileRegistrationController.class)
@Import(SecurityConfig.class)
class TemporaryUserSecurityTest {

	private static final String PROFILE_URL = "/api/v1/users/profile";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProfileRegistrationService registration;

	@Test
	void Bearer_2로_요청하면_사용자_2로_프로필을_등록한다() throws Exception {
		given(registration.register(eq(2L), any(), any(), any()))
				.willReturn(new ProfileResponse(2L, "localtest2", "01000000002", null));

		mockMvc.perform(multipart(PROFILE_URL)
						.param("nickname", "localtest2")
						.param("phone", "01000000002")
						.header(HttpHeaders.AUTHORIZATION, "Bearer 2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(2));

		verify(registration).register(eq(2L), eq("localtest2"), eq("01000000002"), isNull());
	}

	@Test
	void Bearer_없이_1만_보내도_사용자_1로_처리한다() throws Exception {
		given(registration.register(eq(1L), any(), any(), any()))
				.willReturn(new ProfileResponse(1L, "localtest", "01000000000", null));

		mockMvc.perform(multipart(PROFILE_URL)
						.param("nickname", "localtest")
						.param("phone", "01000000000")
						.header(HttpHeaders.AUTHORIZATION, "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.userId").value(1));
	}

	@Test
	void Authorization이_없으면_401이고_컨트롤러를_호출하지_않는다() throws Exception {
		mockMvc.perform(multipart(PROFILE_URL).param("nickname", "localtest").param("phone", "01000000000"))
				.andExpect(status().isUnauthorized());

		verify(registration, never()).register(any(), any(), any(), any());
	}

	@Test
	void 허용되지_않은_사용자_id면_401이다() throws Exception {
		mockMvc.perform(multipart(PROFILE_URL)
						.param("nickname", "localtest")
						.param("phone", "01000000000")
						.header(HttpHeaders.AUTHORIZATION, "Bearer 3"))
				.andExpect(status().isUnauthorized());

		verify(registration, never()).register(any(), any(), any(), any());
	}

	@Test
	void api_경로가_아니면_인증_없이_통과한다() throws Exception {
		// Swagger·health 등은 공개 경로라 401이 아니다(이 슬라이스에는 핸들러가 없어 404)
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isNotFound());
	}

}
