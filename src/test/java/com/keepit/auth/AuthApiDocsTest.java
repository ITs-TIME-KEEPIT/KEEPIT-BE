package com.keepit.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.auth.controller.KakaoLoginController;
import com.keepit.auth.service.KakaoLoginService;
import com.keepit.global.config.SwaggerConfig;
import com.keepit.user.controller.ProfileRegistrationController;
import com.keepit.user.service.ProfileRegistrationService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({KakaoLoginController.class, ProfileRegistrationController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({SwaggerConfig.class, SpringDocConfiguration.class, SpringDocConfigProperties.class,
		SpringDocWebMvcConfiguration.class})
class AuthApiDocsTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean KakaoLoginService login;
	@MockitoBean ProfileRegistrationService registration;

	@Test
	void swaggerSeparatesKakaoLoginFromUserProfileRegistration() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.summary").value("카카오 모바일 로그인"))
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.requestBody").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.parameters").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.security.length()").value(1))
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.security[0].kakaoBearerAuth").isArray())
				.andExpect(jsonPath("$.components.securitySchemes.kakaoBearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.kakaoBearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
				// Authorize 창의 두 입력란은 설명으로 넣을 토큰을 구분한다
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.description",
						Matchers.containsString("백엔드가 발급한 KEEPIT JWT")))
				.andExpect(jsonPath("$.components.securitySchemes.kakaoBearerAuth.description",
						Matchers.containsString("POST /api/v1/auth/kakao/login 하나에만")))
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].get").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/callback']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/profile']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.summary").value("회원 프로필 등록"))
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.tags[0]").value("Users"))
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.requestBody.content['multipart/form-data']").exists());
	}
}
