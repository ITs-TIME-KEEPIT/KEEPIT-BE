package com.keepit.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** [로그인 임시] Authorization의 1·2를 로그인 사용자로 넣는 필터 단위 테스트. */
class TemporaryUserFilterTest {

	private final TemporaryUserFilter filter = new TemporaryUserFilter();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@ValueSource(strings = {"Bearer 1", "1", "Bearer  1 ", " 1"})
	void Bearer_접두사와_상관없이_1이면_사용자_1로_인증한다(String header) throws Exception {
		Authentication auth = authenticate(header);

		assertThat(auth).isNotNull();
		assertThat(auth.getPrincipal()).isEqualTo(1L);
		assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Bearer 2", "2"})
	void 값이_2이면_사용자_2로_인증한다(String header) throws Exception {
		Authentication auth = authenticate(header);

		assertThat(auth).isNotNull();
		assertThat(auth.getPrincipal()).isEqualTo(2L);
	}

	@Test
	void principal은_컨트롤러가_받는_Long_타입이다() throws Exception {
		Authentication auth = authenticate("Bearer 1");

		// @AuthenticationPrincipal Long userId는 타입이 다르면 null이 되므로 Long이어야 한다
		assertThat(auth.getPrincipal()).isInstanceOf(Long.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "Bearer ", "Bearer 3", "0", "-1", "abc", "Bearer 1.0", "Basic 1",
			"Bearer eyJhbGciOiJIUzI1NiJ9.e30.sig"})
	void 허용되지_않은_값이면_인증하지_않는다(String header) throws Exception {
		assertThat(authenticate(header)).isNull();
	}

	@Test
	void Authorization_헤더가_없으면_인증하지_않는다() throws Exception {
		assertThat(authenticate(null)).isNull();
	}

	@Test
	void 인증_여부와_상관없이_다음_필터로_요청을_넘긴다() throws Exception {
		AtomicReference<Boolean> called = new AtomicReference<>(false);

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
				(request, response) -> called.set(true));

		assertThat(called.get()).isTrue();
	}

	/** 필터를 통과시킨 뒤, 다음 필터 시점에 SecurityContext에 들어 있는 인증 정보를 돌려준다. */
	private Authentication authenticate(String header) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/rentals");
		if (header != null) {
			request.addHeader(HttpHeaders.AUTHORIZATION, header);
		}
		AtomicReference<Authentication> captured = new AtomicReference<>();
		filter.doFilter(request, new MockHttpServletResponse(),
				(req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication()));
		return captured.get();
	}

}
