// package com.keepit.auth.service;

// import com.keepit.auth.dto.LoginResult;
// import com.keepit.auth.exception.AuthErrorCode;
// import com.keepit.global.exception.BaseException;
// import com.keepit.global.security.jwt.JwtTokenProvider;
// import com.keepit.user.entity.User;
// import com.keepit.user.entity.UserStatus;
// import com.keepit.user.repository.UserRepository;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// @Service
// @RequiredArgsConstructor
// public class KakaoMemberService {

// 	private final UserRepository users;
// 	private final JwtTokenProvider jwt;
// 	private final LoginTokenService loginTokens;

// 	@Transactional
// 	public LoginResult login(String kakaoId) {
// 		// 첫 로그인에서는 행을 생성하고 재로그인에서는 같은 행의 쓰기 잠금을 유지한다.
// 		users.ensureUserAndLock(kakaoId);
// 		User user = users.findByKakaoId(kakaoId).orElseThrow();

// 		// 탈퇴 회원은 프로필 제출 때 복구하고, 정지 회원에게는 토큰을 발급하지 않는다.
// 		if (user.getStatus() == UserStatus.SUSPENDED) {
// 			throw new BaseException(AuthErrorCode.SUSPENDED);
// 		}
// 		if (user.getStatus() == UserStatus.DELETED || !user.isProfileComplete()) {
// 			return LoginResult.profileRequired(jwt.createSignupToken(user.getId(), user.registrationContext()));
// 		}

// 		// 완성된 프로필만 일반 로그인으로 처리한다.
// 		return LoginResult.authenticated(user.getId(), loginTokens.issue(user));
// 	}

// }
