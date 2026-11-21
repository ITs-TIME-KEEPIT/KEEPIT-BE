package com.keepit.global.security.jwt;

import com.keepit.global.response.ErrorResponse;
import com.keepit.global.response.code.ErrorResponseCode;
import com.keepit.global.response.code.BaseResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증은 됐지만 권한이 없는 요청(403)을 공통 ErrorResponse 형식으로 응답한다.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public JwtAccessDeniedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		write(response, ErrorResponseCode.ACCESS_DENIED_REQUEST);
	}

	// 인증 필터에서 확인된 도메인별 접근 제한도 같은 공통 응답으로 직렬화한다.
	public void write(HttpServletResponse response, BaseResponseCode code) throws IOException {
		ErrorResponse<Void> body = ErrorResponse.from(code);
		response.setStatus(body.getHttpStatus());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}

}
