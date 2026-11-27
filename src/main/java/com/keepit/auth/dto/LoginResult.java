// package com.keepit.auth.dto;

// import com.fasterxml.jackson.annotation.JsonInclude;
// import com.keepit.auth.service.LoginTokenService;

// @JsonInclude(JsonInclude.Include.NON_NULL)
// public record LoginResult(Status status, Long userId, String accessToken, String refreshToken, String signupToken) {

// 	public enum Status { PROFILE_REQUIRED, AUTHENTICATED }

// 	public static LoginResult profileRequired(String signupToken) {
// 		return new LoginResult(Status.PROFILE_REQUIRED, null, null, null, signupToken);
// 	}

// 	public static LoginResult authenticated(Long userId, LoginTokenService.Tokens tokens) {
// 		return new LoginResult(Status.AUTHENTICATED, userId, tokens.accessToken(), tokens.refreshToken(), null);
// 	}

// }
