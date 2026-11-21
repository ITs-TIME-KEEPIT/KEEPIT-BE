package com.keepit.auth.controller;

import com.keepit.auth.controller.docs.AuthApiDocs;
import com.keepit.auth.dto.KakaoLoginRequest;
import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.service.KakaoLoginService;
import com.keepit.global.response.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoLoginController implements AuthApiDocs.KakaoLogin {

	private final KakaoLoginService loginService;

	@Override
	@PostMapping(value = "/api/v1/auth/kakao/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SuccessResponse<LoginResult>> login(@RequestBody KakaoLoginRequest request) {
		var result = loginService.login(request.accessToken());
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.ok(result));
	}
}
