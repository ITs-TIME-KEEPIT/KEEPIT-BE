package com.keepit.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";

	private final JwtTokenProvider jwtTokenProvider =
			new JwtTokenProvider(new JwtProperties(SECRET, 60_000L, 120_000L));

	@Test
	void access_토큰은_access_타입으로_식별된다() {
		String token = jwtTokenProvider.createAccessToken(1L, "USER");

		assertThat(jwtTokenProvider.validateToken(token)).isTrue();
		assertThat(jwtTokenProvider.isAccessToken(token)).isTrue();
		assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
	}

	@Test
	void refresh_토큰은_access_토큰으로_식별되지_않는다() {
		String token = jwtTokenProvider.createRefreshToken(1L);

		assertThat(jwtTokenProvider.validateToken(token)).isTrue();
		assertThat(jwtTokenProvider.isAccessToken(token)).isFalse();
		assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
	}

	@Test
	void secret이_없으면_시작에_실패한다() {
		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties(null, 60_000L, 120_000L)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void secret이_32바이트_미만이면_시작에_실패한다() {
		assertThatThrownBy(() -> new JwtTokenProvider(new JwtProperties("short-secret", 60_000L, 120_000L)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32바이트");
	}

}
