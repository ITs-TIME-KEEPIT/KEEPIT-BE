package com.keepit.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keepit.global.response.SuccessResponse;
import com.keepit.global.response.code.BaseResponseCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void 성공_응답은_공통_형식으로_내려간다() throws Exception {
		mockMvc.perform(get("/test/ok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS_200"))
				.andExpect(jsonPath("$.httpStatus").value(200))
				.andExpect(jsonPath("$.message").value("호출에 성공했습니다."))
				.andExpect(jsonPath("$.timestamp").isNotEmpty())
				.andExpect(jsonPath("$.data").value("hello"));
	}

	@Test
	void 비즈니스_예외는_해당_에러_코드로_응답한다() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("TEST_409"))
				.andExpect(jsonPath("$.httpStatus").value(409))
				.andExpect(jsonPath("$.message").value("테스트 충돌입니다."));
	}

	@Test
	void 요청_바디_검증_실패는_필드_메시지로_400_응답한다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"))
				.andExpect(jsonPath("$.message").value("이름을 입력해 주세요."));
	}

	@Test
	void 읽을_수_없는_요청_바디는_400_응답한다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{invalid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_2"));
	}

	@Test
	void 파라미터_검증_실패는_400_응답한다() throws Exception {
		mockMvc.perform(get("/test/param").param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));
	}

	@Test
	void 필수_파라미터_누락은_400_응답한다() throws Exception {
		mockMvc.perform(get("/test/param"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));
	}

	@Test
	void 파라미터_타입_불일치는_400_응답한다() throws Exception {
		mockMvc.perform(get("/test/path/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GLOBAL_400_3"));
	}

	@Test
	void 존재하지_않는_엔드포인트는_404_응답한다() throws Exception {
		mockMvc.perform(get("/test/unknown"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GLOBAL_404"));
	}

	@Test
	void 지원하지_않는_메소드는_405_응답한다() throws Exception {
		mockMvc.perform(post("/test/ok"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("GLOBAL_405"));
	}

	@Test
	void 지원하지_않는_Content_Type은_415_응답한다() throws Exception {
		mockMvc.perform(post("/test/body")
						.contentType(MediaType.TEXT_PLAIN)
						.content("name"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("GLOBAL_415"));
	}

	@Test
	void 예상하지_못한_예외는_500_응답한다() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("GLOBAL_500"))
				.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
	}

	@RestController
	static class TestController {

		@GetMapping("/test/ok")
		SuccessResponse<String> ok() {
			return SuccessResponse.ok("hello");
		}

		@GetMapping("/test/business")
		void business() {
			throw new BaseException(TestErrorCode.TEST_CONFLICT);
		}

		@PostMapping("/test/body")
		SuccessResponse<Void> body(@Valid @RequestBody TestRequest request) {
			return SuccessResponse.ok();
		}

		@GetMapping("/test/param")
		SuccessResponse<Void> param(@RequestParam @Min(0) int page) {
			return SuccessResponse.ok();
		}

		@GetMapping("/test/path/{id}")
		SuccessResponse<Void> path(@PathVariable Long id) {
			return SuccessResponse.ok();
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("boom");
		}

	}

	record TestRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
	}

	@Getter
	@RequiredArgsConstructor
	enum TestErrorCode implements BaseResponseCode {

		TEST_CONFLICT("TEST_409", HttpStatus.CONFLICT, "테스트 충돌입니다.");

		private final String code;
		private final HttpStatus httpStatus;
		private final String message;

	}

}
