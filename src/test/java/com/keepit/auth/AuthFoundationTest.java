package com.keepit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.keepit.TestcontainersConfiguration;
import com.keepit.auth.service.LoginTokenService;
import com.keepit.global.config.JpaConfig;
import com.keepit.global.security.jwt.JwtProperties;
import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.user.entity.User;
import com.keepit.user.repository.RefreshTokenRepository;
import com.keepit.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaConfig.class})
@ActiveProfiles("test")
class AuthFoundationTest {

	@Autowired UserRepository users;
	@Autowired RefreshTokenRepository refreshTokens;
	final JwtTokenProvider jwt = new JwtTokenProvider(new JwtProperties("test-secret-key-must-be-at-least-32-bytes-long!!", 60000, 120000));

	@Test
	void incompleteUserCanBeStoredAndSignupTokenIsDistinct() {
		User user = users.saveAndFlush(User.builder().kakaoId("pending").build());
		assertThat(users.findByKakaoId("pending")).contains(user);
		assertThat(user.isProfileComplete()).isFalse();
		String token = jwt.createSignupToken(user.getId(), user.registrationContext());
		assertThat(jwt.isSignupToken(token)).isTrue();
		assertThat(jwt.getMemberId(token)).isEqualTo(user.getId());
		assertThat(jwt.isAccessToken(token)).isFalse();
		assertThat(jwt.isRefreshToken(token)).isFalse();
		assertThat(refreshTokens.findByUserId(user.getId())).isEmpty();
	}

	@Test
	void blankFieldsAreIncompleteAndPhotoIsOptional() {
		for (String missing : new String[]{null, "", "   "}) {
			assertThat(User.builder().nickname(missing).phone("01012345678").build().isProfileComplete()).isFalse();
			assertThat(User.builder().nickname("닉네임").phone(missing).build().isProfileComplete()).isFalse();
		}
		assertThat(User.builder().nickname("닉네임").phone("01012345678").build().isProfileComplete()).isTrue();
	}

	@Test
	void repeatedIssuanceRotatesStoredHashEvenWithinOneSecond() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("ready").nickname("회원").phone("01012345678").build());
		LoginTokenService service = new LoginTokenService(jwt, refreshTokens);
		var first = service.issue(user);
		var second = service.issue(user);
		assertThat(jwt.isAccessToken(second.accessToken())).isTrue();
		assertThat(jwt.isRefreshToken(second.refreshToken())).isTrue();
		assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
		var stored = refreshTokens.findByUserId(user.getId()).orElseThrow();
		String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(second.refreshToken().getBytes(StandardCharsets.UTF_8)));
		assertThat(stored.getTokenHash()).isEqualTo(hash);
		assertThat(stored.getExpiresAt()).isEqualTo(jwt.getExpiresAt(second.refreshToken()));
		assertThat(refreshTokens.count()).isOne();
	}
}
