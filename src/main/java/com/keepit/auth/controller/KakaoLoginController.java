package com.keepit.auth.controller;

import com.keepit.auth.controller.docs.AuthApiDocs;
import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.service.KakaoLoginService;
import com.keepit.global.response.SuccessResponse;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoLoginController implements AuthApiDocs.KakaoLogin {

	private static final Pattern KAKAO_TOKEN_HEADER =
			Pattern.compile("(?:(?i:Bearer) +)?([A-Za-z0-9._~+/-]+=*)");

	private final KakaoLoginService loginService;

	@Override
	@PostMapping("/api/v1/auth/kakao/login")
	public ResponseEntity<SuccessResponse<LoginResult>> login(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		// 헤더에서 추출한 카카오 토큰을 기존 로그인 서비스에서 검증한다.
		var result = loginService.login(extractKakaoToken(authorization));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.ok(result));
	}

	private String extractKakaoToken(String authorization) {
		// 헤더나 토큰이 누락되면 서비스의 공통 입력 오류로 처리한다.
		if (authorization == null || "Bearer".equalsIgnoreCase(authorization)) {
			return null;
		}
		// 접두사 유무와 관계없이 형식에 맞는 카카오 토큰 값만 꺼낸다.
		var match = KAKAO_TOKEN_HEADER.matcher(authorization);
		return match.matches() ? match.group(1) : null;
	}
}
