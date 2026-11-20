package com.keepit.auth.service;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.global.exception.BaseException;
import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.image.service.ImageFileStorage;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserStatus;
import com.keepit.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileRegistrationService {

	private final JwtTokenProvider jwt;
	private final UserRepository users;
	private final LoginTokenService tokens;
	private final ImageFileStorage storage;

	@Transactional
	public LoginResult register(String authorization, String nickname, String phone, MultipartFile image) {
		// 요청자 ID는 입력값 대신 서버가 서명한 등록 토큰에서 얻는다.
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
		}
		var identity = jwt.readSignupIdentity(authorization.substring(7))
				.orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
		String cleanNickname = required(nickname);
		String cleanPhone = required(phone);

		// 동시에 제출한 요청이나 발급 이후 정지·탈퇴된 계정을 다시 검사한다.
		User user = users.lockById(identity.memberId()).orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new BaseException(AuthErrorCode.SUSPENDED);
		}
		if (user.getStatus() == UserStatus.ACTIVE && user.isProfileComplete()) {
			throw new BaseException(AuthErrorCode.PROFILE_COMPLETED);
		}
		if (!user.registrationContext().equals(identity.registration())) {
			throw new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
		}

		// 사진은 선택이며, 프로필 갱신과 토큰 해시 저장을 함께 커밋한다.
		String key = image == null ? null : storage.store(image, "profiles");
		user.completeProfile(cleanNickname, cleanPhone, key);
		return LoginResult.authenticated(user.getId(), tokens.issue(user));
	}

	private String required(String input) {
		String value = input == null ? "" : input.strip();
		if (value.isEmpty() || value.codePointCount(0, value.length()) > 20) {
			throw new BaseException(AuthErrorCode.INVALID_PROFILE);
		}
		return value;
	}

}
