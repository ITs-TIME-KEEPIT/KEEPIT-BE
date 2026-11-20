package com.keepit.global.security.jwt;

import com.keepit.global.config.ClockConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";
	private static final String TYPE_CLAIM = "type";
	private static final String ACCESS_TYPE = "access";
	private static final String REFRESH_TYPE = "refresh";
	private static final String SIGNUP_TYPE = "signup";
	private static final String STATE_TYPE = "oauth_state";
	public static final Duration STATE_LIFETIME = Duration.ofMinutes(5);
	private static final Duration SIGNUP_LIFETIME = Duration.ofMinutes(10);
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey secretKey;
	private final long accessTokenExpiration;
	private final long refreshTokenExpiration;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		String secret = jwtProperties.secret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("jwt.secret(JWT_SECRET)은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
		}
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpiration = jwtProperties.accessTokenExpiration();
		this.refreshTokenExpiration = jwtProperties.refreshTokenExpiration();
	}

	public String createAccessToken(Long memberId, String role) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(TYPE_CLAIM, ACCESS_TYPE)
				.claim(ROLE_CLAIM, role)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + accessTokenExpiration))
				.signWith(secretKey)
				.compact();
	}

	public String createRefreshToken(Long memberId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(TYPE_CLAIM, REFRESH_TYPE)
				// 같은 초의 재로그인도 이전 리프레시 토큰과 구분한다.
				.id(UUID.randomUUID().toString())
				.issuedAt(now)
				.expiration(new Date(now.getTime() + refreshTokenExpiration))
				.signWith(secretKey)
				.compact();
	}

	// 프로필 등록용 자격은 회원 ID와 용도만 담아 짧게 유지한다.
	public String createSignupToken(Long memberId) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(TYPE_CLAIM, SIGNUP_TYPE)
				.issuedAt(now)
				.expiration(Date.from(now.toInstant().plus(SIGNUP_LIFETIME)))
				.signWith(secretKey)
				.compact();
	}

	// 서버가 서명한 무작위 상태값으로 같은 브라우저에서 시작한 인가 흐름을 식별한다.
	public String createOAuthState() {
		Date now = new Date();
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.claim(TYPE_CLAIM, STATE_TYPE)
				.issuedAt(now)
				.expiration(Date.from(now.toInstant().plus(STATE_LIFETIME)))
				.signWith(secretKey)
				.compact();
	}

	public boolean isOAuthState(String token) {
		try {
			return STATE_TYPE.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
		} catch (JwtException | IllegalArgumentException exception) {
			return false;
		}
	}

	public boolean isSignupToken(String token) {
		return SIGNUP_TYPE.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
	}

	// JWT 자체의 만료 시각을 읽어 DB와 토큰의 유효 기간을 일치시킨다.
	public LocalDateTime getExpiresAt(String token) {
		return LocalDateTime.ofInstant(parseClaims(token).getExpiration().toInstant(), ClockConfig.SEOUL);
	}

	public boolean validateToken(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (JwtException | IllegalArgumentException e) {
			log.debug("Invalid JWT: {}", e.getMessage());
			return false;
		}
	}

	public boolean isAccessToken(String token) {
		return ACCESS_TYPE.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
	}

	public boolean isRefreshToken(String token) {
		return REFRESH_TYPE.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
	}

	public Authentication getAuthentication(String token) {
		Claims claims = parseClaims(token);
		Long memberId = Long.valueOf(claims.getSubject());
		String role = claims.get(ROLE_CLAIM, String.class);
		List<SimpleGrantedAuthority> authorities = role == null
				? List.of()
				: List.of(new SimpleGrantedAuthority("ROLE_" + role));
		return new UsernamePasswordAuthenticationToken(memberId, null, authorities);
	}

	public Long getMemberId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

}
