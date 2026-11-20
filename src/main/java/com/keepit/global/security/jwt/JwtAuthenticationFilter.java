package com.keepit.global.security.jwt;

import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserStatus;
import com.keepit.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/** SecurityConfig에서 직접 생성하여 서블릿 필터로의 이중 등록을 방지한다. */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwt;
	private final UserRepository users;
	private final JwtAccessDeniedHandler denied;
	private final RequestMatcher skip;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return skip.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		// 보호된 경로에서는 유효한 회원용 bearer 토큰만 DB 조회 대상으로 삼는다.
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}
		var identity = jwt.readRequestIdentity(header.substring(7));
		if (identity.isEmpty()) {
			chain.doFilter(request, response);
			return;
		}

		// 계정·프로필 상태와 권한은 발급 시점과 달라질 수 있어 요청 시 DB를 기준으로 판정한다.
		User user = users.findById(identity.get().memberId()).orElse(null);
		boolean access = !identity.get().signup();
		// 복구 때 세대가 증가하므로 재가입 이전 기기의 토큰을 재사용할 수 없다.
		if (user == null || (access && (user.getStatus() == UserStatus.DELETED
				|| user.getAuthGeneration() != identity.get().generation()))) {
			chain.doFilter(request, response);
			return;
		}
		if (user.getStatus() == UserStatus.SUSPENDED) {
			denied.write(response, AuthErrorCode.SUSPENDED);
			return;
		}
		if (user.getStatus() == UserStatus.DELETED || !user.isProfileComplete()) {
			denied.write(response, AuthErrorCode.PROFILE_REQUIRED);
			return;
		}

		// 다른 기기에서 등록을 이미 마쳤다면 남은 등록 토큰으로 다시 프로필 입력을 요청하지 않는다.
		if (identity.get().signup()) {
			chain.doFilter(request, response);
			return;
		}

		// 현재 활성 회원 권한으로만 서비스 컨트롤러에 진입한다.
		var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
		var auth = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of(authority));
		SecurityContextHolder.getContext().setAuthentication(auth);
		chain.doFilter(request, response);
	}

}
