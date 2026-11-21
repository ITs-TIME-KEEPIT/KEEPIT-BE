package com.keepit.user.service;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.auth.service.LoginTokenService;
import com.keepit.global.exception.BaseException;
import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.image.service.ImageFileStorage;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserStatus;
import com.keepit.user.repository.UserRepository;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileRegistrationService {

	private static final Pattern NICKNAME = Pattern.compile("[\\p{L}\\p{M}\\p{N} ._'’·-]+");
	private static final Pattern PHONE = Pattern.compile("[0-9]{1,20}");

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
		// 제어문자·태그는 원문에서 거부한 뒤 허용된 가장자리 공백만 제거한다.
		if (nickname == null || !NICKNAME.matcher(nickname).matches() || nickname.isBlank()) {
			throw new BaseException(AuthErrorCode.INVALID_NICKNAME);
		}
		String cleanNickname = nickname.strip();
		int nicknameLength = cleanNickname.codePointCount(0, cleanNickname.length());
		if (nicknameLength < 2 || nicknameLength > 10) {
			throw new BaseException(AuthErrorCode.INVALID_NICKNAME);
		}
		if (phone == null || !PHONE.matcher(phone).matches()) {
			throw new BaseException(AuthErrorCode.INVALID_PHONE);
		}

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
		boolean hasImage = image != null && (!image.isEmpty() || StringUtils.hasLength(image.getOriginalFilename()));
		String key = hasImage ? storage.store(image, "profiles") : null;
		if (user.getStatus() == UserStatus.DELETED) {
			// 재가입 전 기기에서 보관한 토큰은 이전 로그인 행을 가리키도록 한다.
			tokens.endPreviousSession(user.getId());
		}
		user.completeProfile(cleanNickname, phone, key);
		try {
			users.flush();
		} catch (DataIntegrityViolationException exception) {
			if (exception.getCause() instanceof ConstraintViolationException violation) {
				String constraint = violation.getConstraintName();
				if ("uk_users_nickname".equals(constraint) || "users.uk_users_nickname".equals(constraint)) {
					throw new BaseException(AuthErrorCode.NICKNAME_TAKEN);
				}
			}
			throw exception;
		}
		return LoginResult.authenticated(user.getId(), tokens.issue(user));
	}

}
