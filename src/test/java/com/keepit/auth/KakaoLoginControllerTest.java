// package com.keepit.auth;

// import static org.mockito.Mockito.mock;
// import static org.mockito.Mockito.verifyNoInteractions;
// import static org.mockito.Mockito.when;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// import com.keepit.auth.controller.KakaoLoginController;
// import com.keepit.auth.dto.LoginResult;
// import com.keepit.auth.service.KakaoLoginService;
// import com.keepit.auth.service.KakaoMemberService;
// import com.keepit.global.exception.GlobalExceptionHandler;
// import com.keepit.global.oauth.kakao.KakaoOAuthClient;
// import com.keepit.global.oauth.kakao.KakaoProperties;
// import com.keepit.global.oauth.kakao.dto.KakaoTokenInfo;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.ValueSource;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.MediaType;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import tools.jackson.databind.json.JsonMapper;

// class KakaoLoginControllerTest {

// 	private KakaoOAuthClient kakao;
// 	private KakaoMemberService members;
// 	private MockMvc mvc;

// 	@BeforeEach
// 	void setUp() {
// 		kakao = mock(KakaoOAuthClient.class);
// 		members = mock(KakaoMemberService.class);
// 		var loginService = new KakaoLoginService(kakao, new KakaoProperties(12345L), members,
// 				JsonMapper.builder().build());
// 		mvc = MockMvcBuilders.standaloneSetup(new KakaoLoginController(loginService))
// 				.setControllerAdvice(new GlobalExceptionHandler())
// 				.build();
// 	}

// 	@ParameterizedTest
// 	@ValueSource(strings = {"Bearer sdk-token", "bearer  sdk-token", "sdk-token"})
// 	void logsInWithKakaoTokenInAuthorizationHeader(String authorization) throws Exception {
// 		when(kakao.getTokenInfo("sdk-token")).thenReturn(new KakaoTokenInfo(42L, 12345L, 3600L));
// 		when(members.login("42")).thenReturn(LoginResult.profileRequired("signup-token"));

// 		mvc.perform(post("/api/v1/auth/kakao/login")
// 				.header(HttpHeaders.AUTHORIZATION, authorization))
// 				.andExpect(status().isOk())
// 				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
// 				.andExpect(jsonPath("$.data.status").value("PROFILE_REQUIRED"))
// 				.andExpect(jsonPath("$.data.signupToken").value("signup-token"));
// 	}

// 	@ParameterizedTest
// 	@ValueSource(strings = {"Bearer expired-token", "expired-token"})
// 	void rejectsAnExpiredKakaoToken(String authorization) throws Exception {
// 		when(kakao.getTokenInfo("expired-token")).thenReturn(new KakaoTokenInfo(42L, 12345L, 0L));

// 		mvc.perform(post("/api/v1/auth/kakao/login")
// 				.header(HttpHeaders.AUTHORIZATION, authorization))
// 				.andExpect(status().isUnauthorized())
// 				.andExpect(jsonPath("$.code").value("AUTH_401_1"));

// 		verifyNoInteractions(members);
// 	}

// 	@Test
// 	void requiresAnAuthorizationHeader() throws Exception {
// 		mvc.perform(post("/api/v1/auth/kakao/login"))
// 				.andExpect(status().isBadRequest())
// 				.andExpect(jsonPath("$.code").value("AUTH_400_1"));

// 		verifyNoInteractions(kakao, members);
// 	}

// 	@ParameterizedTest
// 	@ValueSource(strings = {"Basic sdk-token", "Bearer", "Bearer ", "Bearer sdk token", "Bearer sdk-token,other",
// 			"sdk token", "sdk-token,other"})
// 	void rejectsMalformedAuthorizationHeaders(String authorization) throws Exception {
// 		mvc.perform(post("/api/v1/auth/kakao/login")
// 				.header(HttpHeaders.AUTHORIZATION, authorization))
// 				.andExpect(status().isBadRequest())
// 				.andExpect(jsonPath("$.code").value("AUTH_400_1"));

// 		verifyNoInteractions(kakao, members);
// 	}

// 	@Test
// 	void doesNotAcceptKakaoTokenFromJsonBody() throws Exception {
// 		mvc.perform(post("/api/v1/auth/kakao/login")
// 				.contentType(MediaType.APPLICATION_JSON)
// 				.content("{\"accessToken\":\"sdk-token\"}"))
// 				.andExpect(status().isBadRequest())
// 				.andExpect(jsonPath("$.code").value("AUTH_400_1"));

// 		verifyNoInteractions(kakao, members);
// 	}
// }
