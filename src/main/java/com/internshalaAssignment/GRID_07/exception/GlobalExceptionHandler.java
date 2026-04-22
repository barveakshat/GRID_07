package com.internshalaAssignment.GRID_07.exception;

import com.internshalaAssignment.GRID_07.api.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
		ResourceNotFoundException exception,
		HttpServletRequest request
	) {
		return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(BusinessRuleViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleBusinessRuleViolation(
		BusinessRuleViolationException exception,
		HttpServletRequest request
	) {
		return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(TooManyRequestsException.class)
	public ResponseEntity<ApiErrorResponse> handleTooManyRequests(
		TooManyRequestsException exception,
		HttpServletRequest request
	) {
		return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request.getRequestURI());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(this::formatValidationMessage)
			.orElse("Request validation failed");
		return buildErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleGenericException(Exception exception, HttpServletRequest request) {
		return buildErrorResponse(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Unexpected server error",
			request.getRequestURI()
		);
	}

	private String formatValidationMessage(FieldError fieldError) {
		return fieldError.getField() + " " + fieldError.getDefaultMessage();
	}

	private ResponseEntity<ApiErrorResponse> buildErrorResponse(HttpStatus status, String message, String path) {
		ApiErrorResponse response = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			status.getReasonPhrase(),
			message,
			path
		);
		return ResponseEntity.status(status).body(response);
	}
}
