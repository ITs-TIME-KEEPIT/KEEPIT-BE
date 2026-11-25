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
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT"))
						.addSecuritySchemes(KAKAO_BEARER_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.description("카카오 모바일 SDK에서 받은 액세스 토큰")));
	}

}
