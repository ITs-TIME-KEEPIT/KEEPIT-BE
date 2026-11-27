package com.keepit.user.service;

// [로그인 원본] 로그인 복구 시 아래 import 주석을 풀고, [로그인 임시] import를 지운다.
// import com.keepit.auth.dto.LoginResult;
// import com.keepit.auth.exception.AuthErrorCode;
// import com.keepit.auth.service.LoginTokenService;
// import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.global.exception.BaseException;
import com.keepit.image.service.ImageFileStorage;
import com.keepit.user.dto.ProfileResponse;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserStatus;
import com.keepit.user.exception.UserErrorCode;
import com.keepit.user.repository.UserRepository;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 회원 프로필 등록.
 *
 * <p>[로그인 임시] 등록 토큰 검사와 로그인 토큰 발급을 빼고, Authorization의 1·2로 정한 사용자 프로필을 갱신한다.
 * 시드 사용자는 이미 프로필이 있으므로 등록 완료 여부와 상관없이 다시 등록할 수 있다.
 * 원래 코드는 "[로그인 원본]" 주석으로 남겨 두었다.
 */
@Service
@RequiredArgsConstructor
public class ProfileRegistrationService {

	private static final Pattern NICKNAME = Pattern.compile("[\\p{L}\\p{M}\\p{N} ._'’·-]+");
	private static final Pattern PHONE = Pattern.compile("[0-9]{1,20}");

	// [로그인 원본] private final JwtTokenProvider jwt;
	private final UserRepository users;
	// [로그인 원본] private final LoginTokenService tokens;
	private final ImageFileStorage storage;

	// [로그인 원본] 메서드 전체
	// @Transactional
	// public LoginResult register(String authorization, String nickname, String phone, MultipartFile image) {
	// 	// 요청자 ID는 입력값 대신 서버가 서명한 등록 토큰에서 얻는다.
	// 	if (authorization == null || !authorization.startsWith("Bearer ")) {
	// 		throw new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
	// 	}
	// 	var identity = jwt.readSignupIdentity(authorization.substring(7))
	// 			.orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
	// 	// 제어문자·태그는 원문에서 거부한 뒤 허용된 가장자리 공백만 제거한다.
	// 	if (nickname == null || !NICKNAME.matcher(nickname).matches() || nickname.isBlank()) {
	// 		throw new BaseException(AuthErrorCode.INVALID_NICKNAME);
	// 	}
	// 	String cleanNickname = nickname.strip();
	// 	int nicknameLength = cleanNickname.codePointCount(0, cleanNickname.length());
	// 	if (nicknameLength < 2 || nicknameLength > 10) {
	// 		throw new BaseException(AuthErrorCode.INVALID_NICKNAME);
	// 	}
	// 	if (phone == null || !PHONE.matcher(phone).matches()) {
	// 		throw new BaseException(AuthErrorCode.INVALID_PHONE);
	// 	}
	//
	// 	// 동시에 제출한 요청이나 발급 이후 정지·탈퇴된 계정을 다시 검사한다.
	// 	User user = users.lockById(identity.memberId()).orElseThrow(() -> new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN));
	// 	if (user.getStatus() == UserStatus.SUSPENDED) {
	// 		throw new BaseException(AuthErrorCode.SUSPENDED);
	// 	}
	// 	if (user.getStatus() == UserStatus.ACTIVE && user.isProfileComplete()) {
	// 		throw new BaseException(AuthErrorCode.PROFILE_COMPLETED);
	// 	}
	// 	if (!user.registrationContext().equals(identity.registration())) {
	// 		throw new BaseException(AuthErrorCode.INVALID_SIGNUP_TOKEN);
	// 	}
	//
	// 	// 사진은 선택이며, 프로필 갱신과 토큰 해시 저장을 함께 커밋한다.
	// 	boolean hasImage = image != null && (!image.isEmpty() || StringUtils.hasLength(image.getOriginalFilename()));
	// 	String key = hasImage ? storage.store(image, "profiles") : null;
	// 	if (user.getStatus() == UserStatus.DELETED) {
	// 		// 재가입 전 기기에서 보관한 토큰은 이전 로그인 행을 가리키도록 한다.
	// 		tokens.endPreviousSession(user.getId());
	// 	}
	// 	user.completeProfile(cleanNickname, phone, key);
	// 	try {
	// 		users.flush();
	// 	} catch (DataIntegrityViolationException exception) {
	// 		if (exception.getCause() instanceof ConstraintViolationException violation) {
	// 			String constraint = violation.getConstraintName();
	// 			if ("uk_users_nickname".equals(constraint) || "users.uk_users_nickname".equals(constraint)) {
	// 				throw new BaseException(AuthErrorCode.NICKNAME_TAKEN);
	// 			}
	// 		}
	// 		throw exception;
	// 	}
	// 	return LoginResult.authenticated(user.getId(), tokens.issue(user));
	// }

	// [로그인 임시] 메서드 전체
	@Transactional
	public ProfileResponse register(Long userId, String nickname, String phone, MultipartFile image) {
		// 제어문자·태그는 원문에서 거부한 뒤 허용된 가장자리 공백만 제거한다.
		if (nickname == null || !NICKNAME.matcher(nickname).matches() || nickname.isBlank()) {
			throw new BaseException(UserErrorCode.INVALID_NICKNAME);
		}
		String cleanNickname = nickname.strip();
		int nicknameLength = cleanNickname.codePointCount(0, cleanNickname.length());
		if (nicknameLength < 2 || nicknameLength > 10) {
			throw new BaseException(UserErrorCode.INVALID_NICKNAME);
		}
		if (phone == null || !PHONE.matcher(phone).matches()) {
			throw new BaseException(UserErrorCode.INVALID_PHONE);
		}

		// 등록 토큰 대신 필터가 정한 고정 사용자 행을 잠그고, 정지 회원만 막는다
		User user = users.lockById(userId).orElseThrow(() -> new BaseException(UserErrorCode.USER_NOT_FOUND));
		if (user.getStatus() == UserStatus.SUSPENDED) {
			throw new BaseException(UserErrorCode.SUSPENDED);
		}

		// 사진은 선택이며, 보내지 않으면 기존 사진 키도 비운다(닉네임·전화번호와 함께 덮어쓴다)
		boolean hasImage = image != null
							&& (!image.isEmpty() || StringUtils.hasLength(image.getOriginalFilename()));
		String key = hasImage ? storage.store(image, "profiles") : null;
		user.completeProfile(cleanNickname, phone, key);

		// 닉네임 유니크 제약 위반은 즉시 flush해서 409로 바꾼다
		try {
			users.flush();
		} catch (DataIntegrityViolationException exception) {
			if (exception.getCause() instanceof ConstraintViolationException violation) {
				String constraint = violation.getConstraintName();
				if ("uk_users_nickname".equals(constraint) || "users.uk_users_nickname".equals(constraint)) {
					throw new BaseException(UserErrorCode.NICKNAME_TAKEN);
				}
			}
			throw exception;
		}
		return new ProfileResponse(user.getId(), user.getNickname(), user.getPhone(), user.getProfileImageKey());
	}

}
