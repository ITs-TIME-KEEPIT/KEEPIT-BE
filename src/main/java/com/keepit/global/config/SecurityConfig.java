package com.keepit.global.config;

import com.keepit.user.repository.UserRepository;
import com.keepit.global.security.jwt.JwtAccessDeniedHandler;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import com.keepit.global.security.jwt.JwtAuthenticationEntryPoint;
import com.keepit.global.security.jwt.JwtAuthenticationFilter;
import com.keepit.global.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

	private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
	// 프로필 API는 등록 토큰을 서비스에서 검사하므로 일반 액세스 인증 경로와 분리한다.
	private static final RequestMatcher PUBLIC_OR_REGISTRATION = new OrRequestMatcher(
			PATHS.matcher(HttpMethod.POST, "/api/v1/auth/kakao/login"),
			PATHS.matcher(HttpMethod.POST, "/api/v1/users/profile"),
			PATHS.matcher("/swagger-ui.html"), PATHS.matcher("/swagger-ui/**"),
			PATHS.matcher("/v3/api-docs/**"), PATHS.matcher("/actuator/health"));

	private final JwtTokenProvider jwtTokenProvider;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

	public SecurityConfig(JwtTokenProvider jwtTokenProvider, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
			JwtAccessDeniedHandler jwtAccessDeniedHandler) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
		this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_OR_REGISTRATION).permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(e -> e
						.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler(jwtAccessDeniedHandler))
				.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, users, jwtAccessDeniedHandler, PUBLIC_OR_REGISTRATION), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

}
