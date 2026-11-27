package com.keepit.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	private static final String BEARER_AUTH = "bearerAuth";
	// [로그인 임시] 카카오 스킴은 등록하지 않지만, 주석처리한 AuthApiDocs 복구 시 참조하므로 상수는 남긴다
	public static final String KAKAO_BEARER_AUTH = "kakaoBearerAuth";

	@Bean
	public OpenAPI openAPI() {
		// 기존 KEEPIT JWT를 기본으로 사용하고 카카오 로그인에는 별도 Bearer 입력을 제공한다.
		return new OpenAPI()
				.info(new Info()
						.title("KEEPIT API")
						.description("KEEPIT 백엔드 API 문서")
						.version("v1"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
				.components(new Components()
						// Authorize 창에서 두 입력란을 구분할 수 있도록 넣을 토큰과 적용 범위를 적어 둔다.
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								// [로그인 원본]
								// .bearerFormat("JWT")
								// .description("백엔드가 발급한 KEEPIT JWT 토큰. 로그인 응답의 accessToken을 넣고, "
								// 		+ "프로필 등록 전에는 signupToken을 넣는다. 카카오 로그인을 제외한 모든 API에 적용된다. "
								// 		+ "Bearer 접두사 없이 토큰 값만 입력한다."))
								// [로그인 임시] JWT 대신 고정 사용자 id를 넣는다
								.description("[로그인 임시] 사용자 id를 넣는다. 1=사용자 1, 2=사용자 2. "
										+ "Bearer 접두사 없이 숫자만 입력한다."))
						// [로그인 원본] 카카오 로그인 API를 주석처리한 동안 Authorize 창에서 카카오 토큰 입력란을 숨긴다.
						// 로그인 복구 시 아래 6줄 주석을 풀고, 바로 밑의 [로그인 임시] 닫는 줄을 지운다.
						// .addSecuritySchemes(KAKAO_BEARER_AUTH, new SecurityScheme()
						// 		.type(SecurityScheme.Type.HTTP)
						// 		.scheme("bearer")
						// 		.description("카카오가 발급한 accessToken(카카오 모바일 SDK 로그인 결과). "
						// 				+ "POST /api/v1/auth/kakao/login 하나에만 사용한다. "
						// 				+ "Bearer 접두사 없이 토큰 값만 입력한다.")));
				); // [로그인 임시] components 닫기
	}

}
