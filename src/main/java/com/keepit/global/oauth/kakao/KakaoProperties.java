package com.keepit.global.oauth.kakao;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(@Positive long appId) {
}
