package com.keepit.global.oauth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.keepit.global.oauth.kakao.dto.KakaoTokenInfo;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoOAuthClientTest {

	@Test
	void readsIdentityAppIdAndLifetimeFromKakao() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoOAuthClient client = new KakaoOAuthClient(builder.build());
		server.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer sdk-access-token"))
				.andRespond(withSuccess("""
						{"id":12345678901,"app_id":1659627,"expires_in":3599}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getTokenInfo("sdk-access-token"))
				.isEqualTo(new KakaoTokenInfo(12345678901L, 1659627L, 3599L));
		server.verify();
	}
}
