package com.keepit.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * [로그인 임시] Authorization 헤더의 1 또는 2를 로그인 사용자로 넣는다.
 * "Bearer 1"(앱·Swagger)과 "1"을 모두 받는다. DB에 id 1·2 사용자가 있어야 한다.
 *
 * <p>SecurityConfig에서 직접 생성한다(서블릿 필터 이중 등록 방지). 로그인 복구 시 이 파일을 지우고
 * JwtAuthenticationFilter를 다시 등록한다.
 */
public class TemporaryUserFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final Set<Long> ALLOWED_USER_IDS = Set.of(1L, 2L);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// 허용된 id(1·2)일 때만 인증한다. 없거나 잘못된 값이면 미인증으로 넘겨 /api/v1/**에서 401이 난다
		Long userId = resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
		if (userId != null) {
			// 원래 JWT 필터와 같이 principal에 Long 회원 id를 넣어 컨트롤러의 @AuthenticationPrincipal이 그대로 동작하게 한다
			var auth = new UsernamePasswordAuthenticationToken(userId, null,
					List.of(new SimpleGrantedAuthority("ROLE_USER")));
			SecurityContextHolder.getContext().setAuthentication(auth);
		}
		chain.doFilter(request, response);
	}

	private Long resolve(String header) {
		// "Bearer " 접두사는 있어도 없어도 받고, 숫자 1·2가 아니면 null
		if (header == null || header.isBlank()) {
			return null;
		}
		String value = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
		try {
			Long id = Long.valueOf(value.trim());
			return ALLOWED_USER_IDS.contains(id) ? id : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
