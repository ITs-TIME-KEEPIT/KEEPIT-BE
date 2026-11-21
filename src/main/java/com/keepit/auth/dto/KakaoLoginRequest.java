package com.keepit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoLoginRequest(
		@Schema(description = "카카오 모바일 SDK가 발급한 액세스 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
		String accessToken) {
}
