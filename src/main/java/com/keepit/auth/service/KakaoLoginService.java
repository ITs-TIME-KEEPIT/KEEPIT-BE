package com.keepit.auth.service;

import com.keepit.auth.dto.LoginResult;
import com.keepit.auth.exception.AuthErrorCode;
import com.keepit.global.exception.BaseException;
import com.keepit.global.oauth.kakao.KakaoOAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class KakaoLoginService {

	private final KakaoOAuthClient kakao;
	private final KakaoMemberService members;

	public LoginResult login(String code) {
		// 토큰 교환과 회원번호 확인을 마친 후에만 DB 트랜잭션을 시작한다.
		Long kakaoId;
		try {
			var token = kakao.getToken(code);
			if (token == null || !StringUtils.hasText(token.accessToken())) {
				throw new BaseException(AuthErrorCode.KAKAO_UNAVAILABLE);
			}
			var info = kakao.getUserInfo(token.accessToken());
			if (info == null || info.id() == null || info.id() <= 0) {
				throw new BaseException(AuthErrorCode.KAKAO_UNAVAILABLE);
			}
			kakaoId = info.id();
		} catch (HttpClientErrorException exception) {
			// 카카오의 응답 본문에는 민감 정보가 포함될 수 있어 도메인 오류로만 전달한다.
			throw new BaseException(exception.getStatusCode().value() == 429
					? AuthErrorCode.KAKAO_UNAVAILABLE : AuthErrorCode.KAKAO_AUTH_FAILED);
		} catch (RestClientException exception) {
			throw new BaseException(AuthErrorCode.KAKAO_UNAVAILABLE);
		}
		return members.login(kakaoId.toString());
	}

}
