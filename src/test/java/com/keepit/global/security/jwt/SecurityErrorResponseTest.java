package com.keepit.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorResponseTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void 인증_실패는_공통_형식의_401로_응답한다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new JwtAuthenticationEntryPoint(jsonMapper).commence(new MockHttpServletRequest(), response,
				new InsufficientAuthenticationException("unauthenticated"));

		JsonNode body = jsonMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asString()).isEqualTo("GLOBAL_401");
		assertThat(body.get("httpStatus").asInt()).isEqualTo(401);
		assertThat(body.get("message").asString()).isEqualTo("인증이 필요합니다.");
	}

	@Test
	void 권한_부족은_공통_형식의_403으로_응답한다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		new JwtAccessDeniedHandler(jsonMapper).handle(new MockHttpServletRequest(), response,
				new AccessDeniedException("denied"));

		JsonNode body = jsonMapper.readTree(response.getContentAsString());
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.get("code").asString()).isEqualTo("GLOBAL_403");
		assertThat(body.get("httpStatus").asInt()).isEqualTo(403);
	}

}
