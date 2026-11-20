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
		String access = jwt.createAccessToken(user.getId(), user.getRole().name());
		String refresh = jwt.createRefreshToken(user.getId());
		String hash = hash(refresh);
		var expiresAt = jwt.getExpiresAt(refresh);

		// 기존 행을 갱신하거나 첫 로그인에서만 새 행을 생성한다.
		refreshTokens.findByUserId(user.getId()).ifPresentOrElse(
				stored -> stored.rotate(hash, expiresAt),
				() -> refreshTokens.save(RefreshToken.builder().user(user).tokenHash(hash).expiresAt(expiresAt).build()));
		return new Tokens(access, refresh);
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
