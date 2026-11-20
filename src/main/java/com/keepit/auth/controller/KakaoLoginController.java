package com.keepit.auth.controller;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.auth.service.KakaoLoginService;
import com.keepit.global.exception.BaseException;
import com.keepit.global.response.SuccessResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestParam;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import com.keepit.global.oauth.kakao.KakaoProperties;
import com.keepit.global.security.jwt.JwtTokenProvider;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/kakao")
@RequiredArgsConstructor
public class KakaoLoginController {

	static final String STATE_COOKIE = "keepit_kakao_state";
	private final KakaoOAuthClient kakao;
	private final KakaoProperties properties;
	private final JwtTokenProvider jwt;
	private final KakaoLoginService loginService;

	@GetMapping("/login")
	public ResponseEntity<Void> login() {
		// 인가 요청과 브라우저 쿠키에 같은 상태값을 담아 콜백에서 대조한다.
		String state = jwt.createOAuthState();
		ResponseCookie cookie = stateCookie(state, JwtTokenProvider.STATE_LIFETIME);
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(kakao.authorizeUri(state))
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	@GetMapping("/callback")
	public SuccessResponse<LoginResult> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String error, @RequestParam(required = false) String state,
			@CookieValue(name = STATE_COOKIE, required = false) String cookie, HttpServletResponse response) {
		// 콜백의 성공과 실패 모두 브라우저의 이번 시도 쿠키를 지우고 자격 증명 응답 캐시를 막는다.
		response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO).toString());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		if (state == null || !state.equals(cookie) || !jwt.isOAuthState(state)) {
			throw new BaseException(AuthErrorCode.INVALID_CALLBACK);
		}
		if (error != null || code == null || code.isBlank()) {
			throw new BaseException(AuthErrorCode.INVALID_CALLBACK);
		}

		// 서버가 카카오로부터 확인한 사용자만 회원 처리에 넘긴다.
		return SuccessResponse.ok(loginService.login(code));
	}

	private ResponseCookie stateCookie(String value, Duration age) {
		// 프록시 뒤에서도 공개 콜백 주소의 HTTPS 여부와 실제 경로를 따른다.
		URI callback = URI.create(properties.redirectUri());
		return ResponseCookie.from(STATE_COOKIE, value)
				.path(callback.getPath())
				.httpOnly(true)
				.secure("https".equalsIgnoreCase(callback.getScheme()))
				.sameSite("Lax")
				.maxAge(age)
				.build();
	}

}
