package com.keepit.global.config;

// [로그인 원본] 로그인 복구 시 아래 import 주석을 풀고, [로그인 임시] import를 지운다.
// import com.keepit.user.repository.UserRepository;
// import com.keepit.global.security.jwt.JwtAccessDeniedHandler;
// import org.springframework.http.HttpMethod;
// import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
// import org.springframework.security.web.util.matcher.OrRequestMatcher;
// import org.springframework.security.web.util.matcher.RequestMatcher;
// import com.keepit.global.security.jwt.JwtAuthenticationEntryPoint;
// import com.keepit.global.security.jwt.JwtAuthenticationFilter;
// import com.keepit.global.security.jwt.JwtTokenProvider;
import com.keepit.global.security.TemporaryUserFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * [로그인 임시] 카카오 로그인·JWT를 주석처리한 동안 Authorization 헤더의 1·2로 사용자를 정한다.
 * 원래 코드는 "[로그인 원본]" 주석으로 남겨 두었다. 복구 시 원본 주석을 풀고 [로그인 임시] 코드를 지운다.
 */
@Configuration
public class SecurityConfig {

	// [로그인 원본] 공개 경로 목록과 JWT 필터 의존성
	// private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
	// // 프로필 API는 등록 토큰을 서비스에서 검사하므로 일반 액세스 인증 경로와 분리한다.
	// private static final RequestMatcher PUBLIC_OR_REGISTRATION = new OrRequestMatcher(
	// 		PATHS.matcher(HttpMethod.POST, "/api/v1/auth/kakao/login"),
	// 		PATHS.matcher(HttpMethod.POST, "/api/v1/users/profile"),
	// 		PATHS.matcher("/swagger-ui.html"), PATHS.matcher("/swagger-ui/**"),
	// 		PATHS.matcher("/v3/api-docs/**"), PATHS.matcher("/actuator/health"));
	//
	// private final JwtTokenProvider jwtTokenProvider;
	// private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	// private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
	//
	// public SecurityConfig(JwtTokenProvider jwtTokenProvider, JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
	// 		JwtAccessDeniedHandler jwtAccessDeniedHandler) {
	// 	this.jwtTokenProvider = jwtTokenProvider;
	// 	this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
	// 	this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
	// }

	@Bean
	// [로그인 원본] public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// [로그인 원본]
				// .authorizeHttpRequests(auth -> auth
				// 		.requestMatchers(PUBLIC_OR_REGISTRATION).permitAll()
				// 		.anyRequest().authenticated())
				// .exceptionHandling(e -> e
				// 		.authenticationEntryPoint(jwtAuthenticationEntryPoint)
				// 		.accessDeniedHandler(jwtAccessDeniedHandler))
				// .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, users, jwtAccessDeniedHandler, PUBLIC_OR_REGISTRATION), UsernamePasswordAuthenticationFilter.class)

				// [로그인 임시] API는 Authorization의 1·2로 정한 사용자만, Swagger·health는 누구나 접근한다
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/v1/**").authenticated()
						.anyRequest().permitAll())
				// [로그인 임시] 사용자를 정하지 못한 요청은 서비스까지 내려가 null id로 500이 나지 않게 401로 끝낸다
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				// [로그인 임시] JWT 필터 대신 고정 사용자 필터를 등록한다
				.addFilterBefore(new TemporaryUserFilter(), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

}
