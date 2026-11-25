package com.keepit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.keepit.TestcontainersConfiguration;
import com.keepit.auth.service.KakaoMemberService;
import com.keepit.global.s3.FileUploader;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import com.keepit.global.oauth.kakao.dto.KakaoTokenInfo;
import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.user.entity.User;
import com.keepit.user.repository.RefreshTokenRepository;
import com.keepit.user.repository.UserRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "kakao.app-id=12345")
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
	@Autowired JsonMapper json;
	@MockitoBean FileUploader uploader;

	private ResultActions login(long kakaoId) throws Exception {
		String token = "sdk-token-" + kakaoId;
		when(kakao.getTokenInfo(token)).thenReturn(new KakaoTokenInfo(kakaoId, 12345L, 3600L));
		return submitToken(token);
	}

	private ResultActions submitToken(String token) throws Exception {
		return mvc.perform(post("/api/v1/auth/kakao/login")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void kakaoLoginRejectsMissingOrMalformedAuthorization() throws Exception {
		mvc.perform(post("/api/v1/auth/kakao/login"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_400_1"));
		mvc.perform(post("/api/v1/auth/kakao/login")
				.header(HttpHeaders.AUTHORIZATION, "Basic sdk-token"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_400_1"));
	}

	@Test
	void firstAndInterruptedLoginShareOnePendingUser() throws Exception {
		login(24101).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"))
				.andExpect(jsonPath("$.data.signupToken").isString()).andExpect(jsonPath("$.data.accessToken").doesNotExist())
				.andExpect(header().string("Cache-Control", "no-store"));
		Long id = users.findByKakaoId("24101").orElseThrow().getId();
		login(24101).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"));
		assertThat(users.findByKakaoId("24101").orElseThrow().getId()).isEqualTo(id);
		assertThat(tokens.findByUserId(id)).isEmpty();
	}

	@Test
	void completedSuspendedAndDeletedMembersTakeDifferentRoutes() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24102").nickname("완료").phone("01012345678").build());
		login(24102).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.data.refreshToken").isString());
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", user.getId());
		login(24102).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403_1"));
		jdbc.update("UPDATE users SET status='DELETED' WHERE id=?", user.getId());
		login(24102).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"));
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
		doThrow(new org.springframework.web.client.ResourceAccessException("timeout")).when(kakao).getTokenInfo("network-failure");
		submitToken("network-failure").andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value("AUTH_502_1"));
	}

	@Test
	void kakaoBadRequestDistinguishesTemporaryFailureFromInvalidCredentials() throws Exception {
		when(kakao.getTokenInfo("service-failure")).thenThrow(kakaoError(400, "{\"code\":-1}"));
		submitToken("service-failure").andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AUTH_502_1"));
		when(kakao.getTokenInfo("invalid-token")).thenThrow(kakaoError(400, "{\"code\":-401}"));
		submitToken("invalid-token").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_1"));
		when(kakao.getTokenInfo("expired-token")).thenThrow(kakaoError(401, "{\"code\":-401}"));
		submitToken("expired-token").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_1"));
		when(kakao.getTokenInfo("empty-error")).thenThrow(kakaoError(400, ""));
		submitToken("empty-error").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_1"));
		when(kakao.getTokenInfo("malformed-error")).thenThrow(kakaoError(400, "invalid json"));
		submitToken("malformed-error").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_1"));
	}

	private static HttpClientErrorException kakaoError(int status, String json) {
		return new HttpClientErrorException(HttpStatus.valueOf(status), "Kakao error",
				json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
	}

	private static final String PROFILE = "/api/v1/users/profile";

	@Test
	void submitOptionalPhotoAndLoginAgain() throws Exception {
		var pending = members.login("24110");
		String bearer = "Bearer " + pending.signupToken();
		mvc.perform(multipart(PROFILE).header("Authorization", bearer).param("phone", "  "))
				.andExpect(status().isBadRequest());
		assertThat(users.findByKakaoId("24110").orElseThrow().isProfileComplete()).isFalse();
		mvc.perform(multipart(PROFILE).header("Authorization", bearer).param("nickname", " 새회원 ").param("phone", "01012345678"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"));
		assertThat(users.findByKakaoId("24110").orElseThrow().getNickname()).isEqualTo("새회원");
		mvc.perform(multipart(PROFILE).header("Authorization", bearer).param("nickname", "덮어쓰기").param("phone", "01099990000"))
				.andExpect(status().isConflict());
		login(24110).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"));
	}

	@Test
	void emptyBrowserFileInputIsOptionalButNamedEmptyFileIsRejected() throws Exception {
		String token = "Bearer " + members.login("24115").signupToken();
		var damaged = new MockMultipartFile("profileImage", "empty.jpg", "image/jpeg", new byte[0]);
		mvc.perform(multipart(PROFILE).file(damaged).header("Authorization", token)
				.param("nickname", "닉네임").param("phone", "01012345678"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("IMAGE_400_1"));
		var noSelection = new MockMultipartFile("profileImage", "", "application/octet-stream", new byte[0]);
		mvc.perform(multipart(PROFILE).file(noSelection).header("Authorization", token)
				.param("nickname", "닉네임").param("phone", "01012345678"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"));
	}

	@Test
	void signupCredentialCanOnlyFinishTheSameRegistration() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24111").build());
		String old = members.login("24111").signupToken();
		jdbc.update("UPDATE users SET status='DELETED', deleted_at=NOW(6), delete_reason='탈퇴' WHERE id=?", user.getId());
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + old)
				.param("nickname", "복구").param("phone", "01012345678")).andExpect(status().isUnauthorized());
		String fresh = members.login("24111").signupToken();
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + fresh)
				.param("nickname", "복구").param("phone", "01012345678")).andExpect(status().isOk());
		assertThat(users.findById(user.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void rollbackAlsoDeletesNewPhoto() throws Exception {
		String signup = members.login("24112").signupToken();
		var photo = new MockMultipartFile("profileImage", "face.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49});
		String oversizedKey = "p".repeat(256);
		when(uploader.upload(any(), org.mockito.ArgumentMatchers.eq("profiles"))).thenReturn(oversizedKey);
		// 저장소가 컬럼보다 긴 키를 반환할 때 실제 DB 커밋 실패를 유도한다.
		mvc.perform(multipart(PROFILE).file(photo).header("Authorization", "Bearer " + signup)
				.param("nickname", "롤백").param("phone", "01012345678")).andExpect(status().isInternalServerError());
		assertThat(users.findByKakaoId("24112").orElseThrow().isProfileComplete()).isFalse();
		verify(uploader).delete(oversizedKey);
		assertThat(tokens.findByUserId(jwt.getMemberId(signup))).isEmpty();
	}

	@Test
	void protectedRouteRequiresActiveCompletedUserAndAccessToken() throws Exception {
		String path = "/api/v1/clothes";
		mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("GLOBAL_401"));
		User user = users.saveAndFlush(User.builder().kakaoId("24120").build());
		String signup = members.login("24120").signupToken();
		mvc.perform(get(path).header("Authorization", "Bearer " + signup))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403_2"));
		jdbc.update("UPDATE users SET nickname='회원',phone='01012345678' WHERE id=?", user.getId());
		String access = "Bearer " + members.login("24120").accessToken();
		jdbc.update("UPDATE users SET phone=NULL WHERE id=?", user.getId());
		mvc.perform(get(path).header("Authorization", access)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_403_2"));
		jdbc.update("UPDATE users SET nickname='회원',phone='01012345678' WHERE id=?", user.getId());
		mvc.perform(get(path).header("Authorization", access)).andExpect(status().isOk());
		mvc.perform(get(path).header("Authorization", "Bearer " + signup)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("GLOBAL_401"));
		jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", user.getId());
		mvc.perform(get(path).header("Authorization", access)).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AUTH_403_1"));
		mvc.perform(get(path).header("Authorization", "Bearer " + jwt.createRefreshToken(user.getId())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void profileRequiresSignupCredential() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24121").build());
		mvc.perform(multipart(PROFILE)).andExpect(status().isUnauthorized());
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + jwt.createAccessToken(user.getId(), "USER", 1L)))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/auth/unknown")).andExpect(status().isUnauthorized());
	}

	@Test
	void interruptedSignupCompletesOnceAndThirdLoginRotatesToken() throws Exception {
		var first = login(24130).andExpect(status().isOk()).andReturn().getResponse();
		var firstData = json.readTree(first.getContentAsByteArray()).get("data");
		assertThat(firstData.get("status").asString()).isEqualTo("PROFILE_REQUIRED");
		long userId = jwt.getMemberId(firstData.get("signupToken").asString());

		// 첫 앱 세션을 버리고 두 번째 로그인 응답만 사용한다.
		var second = login(24130).andExpect(status().isOk()).andReturn().getResponse();
		String signup = json.readTree(second.getContentAsByteArray()).get("data").get("signupToken").asString();
		assertThat(jwt.getMemberId(signup)).isEqualTo(userId);
		var submitted = mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + signup)
				.param("nickname", "완료회원").param("phone", "01012345678"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(userId)).andReturn().getResponse();
		String savedHash = tokens.findByUserId(userId).orElseThrow().getTokenHash();
		String access = json.readTree(submitted.getContentAsByteArray()).get("data").get("accessToken").asString();
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + access)).andExpect(status().isOk());

		// 세 번째 로그인에서는 등록 화면 없이 일반 토큰을 받고 동일한 한 행을 회전한다.
		login(24130).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.data.userId").value(userId));
		assertThat(tokens.findByUserId(userId).orElseThrow().getTokenHash()).isNotEqualTo(savedHash);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Long.class, userId)).isOne();
	}

	@Test
	void twoConcurrentProfileSubmissionsHaveOnlyOneWinner() throws Exception {
		String signup = members.login("24131").signupToken();
		var gate = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		java.util.concurrent.Callable<Integer> submit = () -> {
			gate.await();
			return mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + signup)
					.param("nickname", "동시제출").param("phone", "01012345678")).andReturn().getResponse().getStatus();
		};
		try {
			var a = pool.submit(submit);
			var b = pool.submit(submit);
			gate.countDown();
			assertThat(java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 409);
		} finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", Long.class,
				jwt.getMemberId(signup))).isOne();
	}

	@Test
	void enforcesTwoToTenUnicodeCodePointsAndPreservesLeadingPhoneZeroes() throws Exception {
		String token = members.login("24135").signupToken();
		String name = "𠮷김·’N-e\u0301_'";
		assertThat(name.codePointCount(0, name.length())).isEqualTo(10);
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + token)
				.param("nickname", " 𠮷 ").param("phone", "00123456"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_400_2"));
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + token)
				.param("nickname", name + "김").param("phone", "00123456"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_400_2"));
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + token)
				.param("nickname", " " + name + " ").param("phone", "00123456")).andExpect(status().isOk());
		var saved = users.findByKakaoId("24135").orElseThrow();
		assertThat(saved.getNickname()).isEqualTo(name);
		assertThat(jdbc.queryForObject("""
				SELECT COLUMN_TYPE FROM information_schema.columns
				WHERE table_schema=database() AND table_name='users' AND column_name='nickname'
				""", String.class)).isEqualTo("varchar(20)");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.columns
				WHERE table_schema=database() AND table_name='users' AND column_name='auth_generation'
				""", Integer.class)).isZero();
		assertThat(saved.getPhone()).isEqualTo("00123456");
		String access = members.login("24135").accessToken();
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
	}

	@Test
	void twoMembersCompetingForTheSameNicknameCanRetryAfterConflict() throws Exception {
		String first = members.login("24140").signupToken();
		String second = members.login("24141").signupToken();
		var gate = new CountDownLatch(1);
		var pool = Executors.newFixedThreadPool(2);
		try {
			var one = pool.submit(() -> { gate.await(); return register(first, " Café ").andReturn().getResponse(); });
			var two = pool.submit(() -> { gate.await(); return register(second, "cafe").andReturn().getResponse(); });
			gate.countDown();
			var a = one.get(30, TimeUnit.SECONDS);
			var b = two.get(30, TimeUnit.SECONDS);
			assertThat(java.util.List.of(a.getStatus(), b.getStatus())).containsExactlyInAnyOrder(200, 409);
			var conflict = a.getStatus() == 409 ? a : b;
			assertThat(json.readTree(conflict.getContentAsByteArray()).path("code").asString()).isEqualTo("AUTH_409_2");
			register(a.getStatus() == 409 ? first : second, "새별").andExpect(status().isOk());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void withdrawnMemberReservesNicknameUntilChoosingAnotherOne() throws Exception {
		User previous = users.saveAndFlush(User.builder().kakaoId("24142").nickname("  Olive ").phone("01000000000").build());
		jdbc.update("UPDATE users SET status='DELETED' WHERE id=?", previous.getId());
		String pending = members.login("24143").signupToken();
		register(pending, "olive").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_409_2"));
		register(members.login("24142").signupToken(), "Lemon").andExpect(status().isOk());
		register(pending, "olive").andExpect(status().isOk());
	}

	private ResultActions register(String token, String nickname) throws Exception {
		return mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + token)
				.param("nickname", nickname).param("phone", "01000000000"));
	}

	@Test
	void rejectsMarkupControlsAndNonAsciiPhoneCharacters() throws Exception {
		String token = "Bearer " + members.login("24136").signupToken();
		for (String nickname : java.util.List.of("<b>이름</b>", "이름\n끝", "이름\u202E끝", "   ")) {
			mvc.perform(multipart(PROFILE).header("Authorization", token)
					.param("nickname", nickname).param("phone", "01012345678")).andExpect(status().isBadRequest());
		}
		for (String phone : java.util.List.of(" 0101234", "0101234 ", "010-1234", "+8210", "１２３", "١٢٣", "01\n02")) {
			mvc.perform(multipart(PROFILE).header("Authorization", token)
					.param("nickname", "이름").param("phone", phone)).andExpect(status().isBadRequest());
		}
		assertThat(users.findByKakaoId("24136").orElseThrow().isProfileComplete()).isFalse();
	}

	@Test
	void protectedRouteDoesNotTreatLegacyWhitespaceAsACompleteProfile() throws Exception {
		User existing = users.saveAndFlush(User.builder().kakaoId("24138").nickname("공백검사계정").phone("01012345678").build());
		String auth = "Bearer " + members.login("24138").accessToken();
		for (String blank : java.util.List.of(" ", "\t\n", "\u2003\u3000", "\u001c")) {
			jdbc.update("UPDATE users SET nickname=? WHERE id=?", blank, existing.getId());
			mvc.perform(get("/api/v1/clothes").header("Authorization", auth))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_403_2"));
		}
	}

	@Test
	void existingProfileWithFormattedPhoneRemainsUsable() throws Exception {
		User existing = users.saveAndFlush(User.builder().kakaoId("24137").nickname("기존 회원").phone("010-1234-5678").build());
		String first = members.login("24137").accessToken();
		members.login("24137");
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + first)).andExpect(status().isOk());
		assertThat(users.findById(existing.getId()).orElseThrow().getPhone()).isEqualTo("010-1234-5678");
	}

	@Test
	void rejoiningKeepsOldDevicesSignedOut() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24132").nickname("첫가입").phone("01012345678").build());
		String beforeDeletion = members.login("24132").accessToken();
		var secret = io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long!!"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String legacyAccess = io.jsonwebtoken.Jwts.builder().subject(user.getId().toString()).claim("type", "access")
				.claim("role", "USER").expiration(new java.util.Date(System.currentTimeMillis() + 60000))
				.signWith(secret).compact();
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + legacyAccess)).andExpect(status().isUnauthorized());
		jdbc.update("UPDATE users SET status='DELETED',deleted_at=NOW(6) WHERE id=?", user.getId());
		var restore = members.login("24132");
		var restored = mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + restore.signupToken())
				.param("nickname", "재가입").param("phone", "01077778888")).andExpect(status().isOk())
				.andReturn().getResponse();
		String newAccess = json.readTree(restored.getContentAsByteArray()).get("data").get("accessToken").asString();
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + beforeDeletion))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + legacyAccess)).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/clothes").header("Authorization", "Bearer " + newAccess)).andExpect(status().isOk());
	}

	@Test
	void expiredSignupTokenCannotFinishProfile() throws Exception {
		User user = users.saveAndFlush(User.builder().kakaoId("24133").build());
		var secret = io.jsonwebtoken.security.Keys.hmacShaKeyFor("test-secret-key-must-be-at-least-32-bytes-long!!"
				.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		String expired = io.jsonwebtoken.Jwts.builder().subject(user.getId().toString()).claim("type", "signup")
				.claim("registration", user.registrationContext()).expiration(new java.util.Date(1)).signWith(secret).compact();
		mvc.perform(multipart(PROFILE).header("Authorization", "Bearer " + expired)
				.param("nickname", "회원").param("phone", "01012345678")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_401_2"));
		assertThat(users.findById(user.getId()).orElseThrow().isProfileComplete()).isFalse();
		assertThat(tokens.findByUserId(user.getId())).isEmpty();
	}

	@Test
	void sdkTokenMustBePresentUnexpiredAndIssuedToOurApp() throws Exception {
		submitToken(" ").andExpect(status().isBadRequest());
		when(kakao.getTokenInfo("wrong-app")).thenReturn(new KakaoTokenInfo(24190L, 12346L, 1800L));
		submitToken("wrong-app").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_1"));
		when(kakao.getTokenInfo("expired")).thenReturn(new KakaoTokenInfo(24190L, 12345L, 0L));
		submitToken("expired").andExpect(status().isUnauthorized());
		when(kakao.getTokenInfo("no-member")).thenReturn(new KakaoTokenInfo(null, 12345L, 3600L));
		submitToken("no-member").andExpect(status().isBadGateway());
		assertThat(users.findByKakaoId("24190")).isEmpty();
	}
}
