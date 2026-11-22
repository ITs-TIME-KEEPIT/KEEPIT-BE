package com.keepit.auth.service;

import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.user.entity.RefreshToken;
import com.keepit.user.entity.User;
import com.keepit.user.entity.UserStatus;
import com.keepit.user.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginTokenService {

	private final JwtTokenProvider jwt;
	private final RefreshTokenRepository refreshTokens;

	public record Tokens(String accessToken, String refreshToken) {}

	// 회원 잠금을 잡은 호출자와 같은 트랜잭션에서 토큰까지 저장한다.
	@Transactional(propagation = Propagation.MANDATORY)
	public Tokens issue(User user) {
		if (user.getStatus() != UserStatus.ACTIVE || !user.isProfileComplete()) {
			throw new IllegalStateException("활성 상태에서 프로필을 완성한 회원만 로그인할 수 있습니다.");
		}

		// 리프레시 토큰 원문은 클라이언트에만 전달하고 DB에는 해시를 남긴다.
		String refresh = jwt.createRefreshToken(user.getId());
		String hash = hash(refresh);
		var expiresAt = jwt.getExpiresAt(refresh);

		// 일반 재로그인은 같은 행을 유지해 다른 기기의 액세스 토큰이 만료될 때까지 유효하게 한다.
		RefreshToken session = refreshTokens.findByUserId(user.getId()).orElse(null);
		if (session == null) {
			session = refreshTokens.save(RefreshToken.builder().user(user).tokenHash(hash).expiresAt(expiresAt).build());
		} else {
			session.rotate(hash, expiresAt);
		}
		String access = jwt.createAccessToken(user.getId(), user.getRole().name(), session.getId());
		return new Tokens(access, refresh);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void endPreviousSession(Long userId) {
		refreshTokens.deleteSession(userId);
	}

	private String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}

}
