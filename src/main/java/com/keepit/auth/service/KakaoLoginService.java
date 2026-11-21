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

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

	private final KakaoOAuthClient kakao;
	private final KakaoProperties properties;
	private final KakaoMemberService members;

	public LoginResult login(String accessToken) {
		if (accessToken == null || accessToken.isBlank() || accessToken.chars().anyMatch(c -> c <= 32 || c >= 127)) {
			throw new BaseException(AuthErrorCode.INVALID_KAKAO_TOKEN_INPUT);
		}
		KakaoTokenInfo info;
		try {
			info = kakao.getTokenInfo(accessToken);
		} catch (HttpClientErrorException error) {
			int status = error.getStatusCode().value();
			throw new BaseException(status == 400 || status == 401 || status == 403
					? AuthErrorCode.KAKAO_AUTH_FAILED : AuthErrorCode.KAKAO_UNAVAILABLE);
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
}
