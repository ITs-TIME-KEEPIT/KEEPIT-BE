package com.keepit.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서비스가 "지금"을 직접 만들지 않고 주입받도록 한다. 테스트에서는 고정된 Clock으로 바꿔 끼운다.
 */
@Configuration
public class ClockConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}

}
