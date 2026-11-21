package com.keepit.auth.service;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.global.exception.BaseException;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import com.keepit.global.oauth.kakao.KakaoProperties;
import com.keepit.global.oauth.kakao.dto.KakaoTokenInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

	private final KakaoOAuthClient kakao;
	private final KakaoProperties properties;
	private final KakaoMemberService members;
	private final JsonMapper json;

	public LoginResult login(String accessToken) {
		if (accessToken == null || accessToken.isBlank() || accessToken.chars().anyMatch(c -> c <= 32 || c >= 127)) {
			throw new BaseException(AuthErrorCode.INVALID_KAKAO_TOKEN_INPUT);
		}
		KakaoTokenInfo info;
		try {
			info = kakao.getTokenInfo(accessToken);
		} catch (HttpClientErrorException error) {
			throw new BaseException(mapKakaoError(error));
		} catch (RestClientException error) {
			throw new BaseException(AuthErrorCode.KAKAO_UNAVAILABLE);
		}

		// 카카오가 확인한 앱과 회원만 신뢰하고 검증 완료 후에 회원 트랜잭션을 시작한다.
		if (info == null || info.appId() == null || info.id() == null || info.id() <= 0 || info.expiresIn() == null) {
			throw new BaseException(AuthErrorCode.KAKAO_UNAVAILABLE);
		}
		if (info.appId() != properties.appId() || info.expiresIn() <= 0) {
			throw new BaseException(AuthErrorCode.KAKAO_AUTH_FAILED);
		}
		return members.login(info.id().toString());
	}

	private AuthErrorCode mapKakaoError(HttpClientErrorException error) {
		int status = error.getStatusCode().value();
		if (status == 400) {
			// 카카오 토큰 정보 API는 일시적 플랫폼 장애도 HTTP 400으로 응답한다.
			try {
				var code = json.readTree(error.getResponseBodyAsByteArray()).path("code");
				if (code.isIntegralNumber() && code.asLong() == -1) {
					return AuthErrorCode.KAKAO_UNAVAILABLE;
				}
			} catch (JacksonException ignored) {
				// JSON 오류 코드가 없으면 기존 HTTP 상태 매핑을 따른다.
			}
		}
		return status == 400 || status == 401 || status == 403
				? AuthErrorCode.KAKAO_AUTH_FAILED : AuthErrorCode.KAKAO_UNAVAILABLE;
	}
}
