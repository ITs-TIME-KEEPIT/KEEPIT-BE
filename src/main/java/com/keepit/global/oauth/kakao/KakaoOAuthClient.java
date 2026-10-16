package com.keepit.global.oauth.kakao;

import com.keepit.global.oauth.kakao.dto.KakaoTokenResponse;
import com.keepit.global.oauth.kakao.dto.KakaoUserInfoResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인 (REST API) 클라이언트.
 * 1) 프론트에서 받은 인가 코드로 카카오 액세스 토큰 발급
 * 2) 카카오 액세스 토큰으로 사용자 정보 조회
 */
@Component
public class KakaoOAuthClient {

	private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
	private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

	private final KakaoProperties kakaoProperties;
	private final RestClient restClient = RestClient.create();

	public KakaoOAuthClient(KakaoProperties kakaoProperties) {
		this.kakaoProperties = kakaoProperties;
	}

	public KakaoTokenResponse getToken(String authorizationCode) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", kakaoProperties.clientId());
		form.add("redirect_uri", kakaoProperties.redirectUri());
		form.add("code", authorizationCode);
		if (StringUtils.hasText(kakaoProperties.clientSecret())) {
			form.add("client_secret", kakaoProperties.clientSecret());
		}

		return restClient.post()
				.uri(TOKEN_URI)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(KakaoTokenResponse.class);
	}

	public KakaoUserInfoResponse getUserInfo(String kakaoAccessToken) {
		return restClient.get()
				.uri(USER_INFO_URI)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
				.retrieve()
				.body(KakaoUserInfoResponse.class);
	}

}
