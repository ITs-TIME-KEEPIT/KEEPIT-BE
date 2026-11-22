package com.keepit.user.controller;

import com.keepit.auth.dto.LoginResult;
import com.keepit.global.response.SuccessResponse;
import com.keepit.user.controller.docs.UserProfileApiDocs;
import com.keepit.user.service.ProfileRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ProfileRegistrationController implements UserProfileApiDocs {

	private final ProfileRegistrationService registration;

	@PostMapping(value = "/api/v1/users/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Override
	public ResponseEntity<SuccessResponse<LoginResult>> register(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(required = false) String nickname, @RequestParam(required = false) String phone,
			@RequestParam(required = false) MultipartFile profileImage) {
		// 저장이 커밋된 후에만 일반 로그인 토큰을 캐시할 수 없는 응답으로 반환한다.
		var result = registration.register(authorization, nickname, phone, profileImage);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(SuccessResponse.ok(result));
	}

}
