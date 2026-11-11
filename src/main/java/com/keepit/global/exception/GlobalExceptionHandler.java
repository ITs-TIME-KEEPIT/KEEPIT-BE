package com.keepit.global.exception;

import com.keepit.global.response.ErrorResponse;
import com.keepit.global.response.code.BaseResponseCode;
import com.keepit.global.response.code.ErrorResponseCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
	 * @Valid 요청 바디·바인딩 검증 실패. (MethodArgumentNotValidException은 BindException의 하위 타입)
	 * 첫 번째 필드 에러의 메시지를 응답 메시지로 쓴다.
	 */
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse<Void>> handleBindException(BindException exception) {
		log.warn("Validation failed: {}", exception.getMessage());
		ErrorResponseCode responseCode = exception instanceof MethodArgumentNotValidException validationException
				&& (validationException.getParameter().hasParameterAnnotation(RequestBody.class)
						|| validationException.getParameter().hasParameterAnnotation(RequestPart.class))
				? ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY
				: ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER;
		FieldError fieldError = exception.getBindingResult().getFieldError();
		if (fieldError == null || fieldError.getDefaultMessage() == null) {
			return response(ErrorResponse.from(responseCode));
		}
		return response(ErrorResponse.of(responseCode, fieldError.getDefaultMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse<Void>> handleMessageNotReadableException(HttpMessageNotReadableException exception) {
		log.warn("Invalid request body: {}", exception.getMessage());
		return response(ErrorResponse.from(ErrorResponseCode.INVALID_HTTP_MESSAGE_BODY));
	}

	@ExceptionHandler({
			HandlerMethodValidationException.class,
			ConstraintViolationException.class,
			MethodArgumentTypeMismatchException.class,
			MissingServletRequestParameterException.class
	})
	public ResponseEntity<ErrorResponse<Void>> handleParameterException(Exception exception) {
		log.warn("Invalid request parameter: {}", exception.getMessage());
		return response(ErrorResponse.from(ErrorResponseCode.INVALID_HTTP_MESSAGE_PARAMETER));
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ErrorResponse<Void>> handleNotFoundException(Exception exception) {
		return response(ErrorResponse.from(ErrorResponseCode.NOT_FOUND_ENDPOINT));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse<Void>> handleMethodNotSupportedException(
			HttpRequestMethodNotSupportedException exception) {
		ErrorResponse<Void> body = ErrorResponse.from(ErrorResponseCode.UNSUPPORTED_HTTP_METHOD);
		return ResponseEntity.status(body.getHttpStatus()).headers(exception.getHeaders()).body(body);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse<Void>> handleMediaTypeNotSupportedException(
			HttpMediaTypeNotSupportedException exception) {
		return response(ErrorResponse.from(ErrorResponseCode.UNSUPPORTED_MEDIA_TYPE));
	}

	@ExceptionHandler(BaseException.class)
	public ResponseEntity<ErrorResponse<Void>> handleBaseException(BaseException exception) {
		BaseResponseCode responseCode = exception.getResponseCode();
		log.info("Business exception: {} {}", responseCode.getCode(), exception.getMessage());
		return response(ErrorResponse.from(responseCode));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unexpected server error", exception);
		return response(ErrorResponse.from(ErrorResponseCode.SERVER_ERROR));
	}

	private ResponseEntity<ErrorResponse<Void>> response(ErrorResponse<Void> errorResponse) {
		return ResponseEntity.status(errorResponse.getHttpStatus()).body(errorResponse);
	}

}
