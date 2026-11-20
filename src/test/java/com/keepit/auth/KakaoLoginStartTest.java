package com.keepit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.auth.controller.KakaoLoginController;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import com.keepit.global.oauth.kakao.KakaoProperties;
import com.keepit.global.security.jwt.JwtProperties;
import com.keepit.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

class KakaoLoginStartTest {

	@Test
	void loginRedirectBindsUniqueStateToCallbackCookie() {
		var jwt = new JwtTokenProvider(new JwtProperties("test-secret-key-must-be-at-least-32-bytes-long!!", 60000, 120000));
		var properties = new KakaoProperties("app-key", "local-test-secret", "https://keepit.test/api/v1/auth/kakao/callback");
		var controller = new KakaoLoginController(new KakaoOAuthClient(properties), properties, jwt, org.mockito.Mockito.mock(com.keepit.auth.service.KakaoLoginService.class));

		var response = controller.login();
		var uri = UriComponentsBuilder.fromUri(response.getHeaders().getLocation()).build();
		String state = uri.getQueryParams().getFirst("state");

		assertThat(response.getStatusCode().value()).isEqualTo(302);
		assertThat(uri.getHost()).isEqualTo("kauth.kakao.com");
		assertThat(uri.getPath()).isEqualTo("/oauth/authorize");
		assertThat(uri.getQueryParams().getFirst("client_id")).isEqualTo("app-key");
		assertThat(java.net.URLDecoder.decode(uri.getQueryParams().getFirst("redirect_uri"), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(properties.redirectUri());
		assertThat(uri.getQueryParams()).doesNotContainKey("client_secret");
		assertThat(response.getHeaders().getFirst("Set-Cookie")).contains(state, "Path=/api/v1/auth/kakao/callback",
				"HttpOnly", "SameSite=Lax", "Secure");
		assertThat(jwt.isOAuthState(state)).isTrue();
		assertThat(jwt.isAccessToken(state)).isFalse();
		assertThat(controller.login().getHeaders().getLocation()).isNotEqualTo(response.getHeaders().getLocation());
	}
}
