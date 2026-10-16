package com.keepit.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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

	private final SecretKey secretKey;
	private final long accessTokenExpiration;
	private final long refreshTokenExpiration;

	public JwtTokenProvider(JwtProperties jwtProperties) {
		this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenExpiration = jwtProperties.accessTokenExpiration();
		this.refreshTokenExpiration = jwtProperties.refreshTokenExpiration();
	}

	public String createAccessToken(Long memberId, String role) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(memberId))
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
				.issuedAt(now)
				.expiration(new Date(now.getTime() + refreshTokenExpiration))
				.signWith(secretKey)
				.compact();
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
