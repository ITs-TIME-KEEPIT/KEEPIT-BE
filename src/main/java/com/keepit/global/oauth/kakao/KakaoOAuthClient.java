package com.keepit.global.oauth.kakao;

import com.keepit.global.oauth.kakao.dto.KakaoTokenInfo;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthClient {

	private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
	private final RestClient restClient;

	public KakaoOAuthClient() {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	KakaoOAuthClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public KakaoTokenInfo getTokenInfo(String accessToken) {
		return restClient.get()
				.uri(TOKEN_INFO_URI)
				.headers(headers -> headers.setBearerAuth(accessToken))
				.retrieve()
				.body(KakaoTokenInfo.class);
	}
}
