package com.keepit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.keepit.TestcontainersConfiguration;
import com.keepit.auth.service.KakaoMemberService;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import com.keepit.global.oauth.kakao.dto.KakaoTokenResponse;
import com.keepit.global.oauth.kakao.dto.KakaoUserInfoResponse;
import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.user.entity.User;
import com.keepit.user.repository.RefreshTokenRepository;
import com.keepit.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "kakao.redirect-uri=https://keepit.test/api/v1/auth/kakao/callback")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KakaoFlowIntegrationTest {

	@Autowired MockMvc mvc;
	@Autowired JwtTokenProvider jwt;
	@Autowired UserRepository users;
	@Autowired RefreshTokenRepository tokens;
	@Autowired JdbcTemplate jdbc;
	@Autowired KakaoMemberService members;
	@MockitoBean KakaoOAuthClient kakao;

	private ResultActions callback(long kakaoId) throws Exception {
		String code = "code-" + kakaoId;
		when(kakao.getToken(code)).thenReturn(new KakaoTokenResponse("bearer", code, 100, null, null, null));
		when(kakao.getUserInfo(code)).thenReturn(new KakaoUserInfoResponse(kakaoId, null));
		String state = jwt.createOAuthState();
		return mvc.perform(get("/api/v1/auth/kakao/callback").param("state", state).param("code", code)
				.cookie(new Cookie("keepit_kakao_state", state)));
	}

	@Test
	void firstAndInterruptedLoginShareOnePendingUser() throws Exception {
		callback(24101).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"))
				.andExpect(jsonPath("$.data.signupToken").isString()).andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(cookie().maxAge("keepit_kakao_state", 0));
		Long id = users.findByKakaoId("24101").orElseThrow().getId();
		callback(24101).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"));
		assertThat(users.findByKakaoId("24101").orElseThrow().getId()).isEqualTo(id);
		assertThat(tokens.findByUserId(id)).isEmpty();
	}

	@Test
	void completedSuspendedAndDeletedMembersTakeDifferentRoutes() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24102").nickname("완료").phone("01012345678").build());
		callback(24102).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.data.refreshToken").isString());
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", user.getId());
		callback(24102).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403_1"));
		jdbc.update("UPDATE users SET status='DELETED' WHERE id=?", user.getId());
		callback(24102).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"));
	}

	@Test
	void simultaneousFirstLoginCreatesOneMember() throws Exception {
		var pool = Executors.newFixedThreadPool(2);
		var gate = new CountDownLatch(1);
		try {
			var a = pool.submit(() -> { gate.await(); return members.login("24103"); });
			var b = pool.submit(() -> { gate.await(); return members.login("24103"); });
			gate.countDown();
			Long first = jwt.getMemberId(a.get(30, TimeUnit.SECONDS).signupToken());
			Long second = jwt.getMemberId(b.get(30, TimeUnit.SECONDS).signupToken());
			assertThat(first).isEqualTo(second);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE kakao_id='24103'", Long.class))
					.isEqualTo(1L);
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void upstreamFailureProducesCommonErrorBeforeUserWrite() throws Exception {
		when(kakao.getToken("network-failure")).thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));
		String state = jwt.createOAuthState();
		mvc.perform(get("/api/v1/auth/kakao/callback").param("state", state).param("code", "network-failure")
				.cookie(new Cookie("keepit_kakao_state", state))).andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("AUTH_502_1"));
	}

	@Test
	void callbackRejectsMissingBrowserStateAndHandlesCancellation() throws Exception {
		mvc.perform(get("/api/v1/auth/kakao/callback").param("code", "123"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
		String state = jwt.createOAuthState();
		mvc.perform(get("/api/v1/auth/kakao/callback").param("state", state).param("error", "access_denied")
				.cookie(new Cookie("keepit_kakao_state", state))).andExpect(status().isBadRequest());
	}
}
