// package com.keepit.global.security.jwt;

// import com.keepit.auth.exception.AuthErrorCode;
// import com.keepit.user.entity.UserStatus;
// import com.keepit.user.repository.UserRepository;
// import jakarta.servlet.FilterChain;
// import jakarta.servlet.ServletException;
// import jakarta.servlet.http.HttpServletRequest;
// import jakarta.servlet.http.HttpServletResponse;
// import java.io.IOException;
// import java.util.List;
// import lombok.RequiredArgsConstructor;
// import org.springframework.http.HttpHeaders;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.security.core.context.SecurityContextHolder;
// import org.springframework.security.web.util.matcher.RequestMatcher;
// import org.springframework.web.filter.OncePerRequestFilter;

// /** SecurityConfig에서 직접 생성하여 서블릿 필터로의 이중 등록을 방지한다. */
// @RequiredArgsConstructor
// public class JwtAuthenticationFilter extends OncePerRequestFilter {

// 	private final JwtTokenProvider jwt;
// 	private final UserRepository users;
// 	private final JwtAccessDeniedHandler denied;
// 	private final RequestMatcher skip;

// 	@Override
// 	protected boolean shouldNotFilter(HttpServletRequest request) {
// 		return skip.matches(request);
// 	}

// 	@Override
// 	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
// 			throws ServletException, IOException {
// 		// 보호된 경로에서는 유효한 회원용 bearer 토큰만 DB 조회 대상으로 삼는다.
// 		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
// 		if (header == null || !header.startsWith("Bearer ")) {
// 			chain.doFilter(request, response);
// 			return;
// 		}
// 		var identity = jwt.readRequestIdentity(header.substring(7));
// 		if (identity.isEmpty()) {
// 			chain.doFilter(request, response);
// 			return;
// 		}

// 		// 계정·프로필 상태와 권한은 발급 시점과 달라질 수 있어 요청 시 DB를 기준으로 판정한다.
// 		var account = users.findAccountSession(identity.get().memberId()).orElse(null);
// 		if (account == null) {
// 			chain.doFilter(request, response);
// 			return;
// 		}

// 		// 탈퇴 후 교체된 로그인 행을 가리키거나 행 식별자가 없는 이전 액세스 토큰은 재인증한다.
// 		if (!identity.get().signup() && (account.getStatus() == UserStatus.DELETED
// 				|| account.getSessionId() == null || !account.getSessionId().equals(identity.get().sessionId()))) {
// 			chain.doFilter(request, response);
// 			return;
// 		}
// 		if (account.getStatus() == UserStatus.SUSPENDED) {
// 			denied.write(response, AuthErrorCode.SUSPENDED);
// 			return;
// 		}
// 		if (account.getStatus() == UserStatus.DELETED || account.getProfileComplete() == 0) {
// 			denied.write(response, AuthErrorCode.PROFILE_REQUIRED);
// 			return;
// 		}

// 		// 다른 기기에서 등록을 이미 마쳤다면 남은 등록 토큰으로 다시 프로필 입력을 요청하지 않는다.
// 		if (identity.get().signup()) {
// 			chain.doFilter(request, response);
// 			return;
// 		}

// 		// 현재 활성 회원 권한으로만 서비스 컨트롤러에 진입한다.
// 		var authority = new SimpleGrantedAuthority("ROLE_" + account.getRole().name());
// 		var auth = new UsernamePasswordAuthenticationToken(account.getMemberId(), null, List.of(authority));
// 		SecurityContextHolder.getContext().setAuthentication(auth);
// 		chain.doFilter(request, response);
// 	}

// }
