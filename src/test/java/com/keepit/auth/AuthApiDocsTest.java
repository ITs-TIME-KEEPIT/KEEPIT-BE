package com.keepit.auth;

import com.keepit.TestcontainersConfiguration;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthApiDocsTest {

	@Autowired
	MockMvc mvc;

	@Test
	void swaggerSeparatesKakaoLoginFromUserProfileRegistration() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.summary").value("카카오 모바일 로그인"))
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.requestBody.content['application/json']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.security").isArray())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].post.security").isEmpty())
				.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].get").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/callback']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/auth/kakao/profile']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.summary").value("회원 프로필 등록"))
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.tags[0]").value("Users"))
				.andExpect(jsonPath("$.paths['/api/v1/users/profile'].post.requestBody.content['multipart/form-data']").exists());
	}
}
