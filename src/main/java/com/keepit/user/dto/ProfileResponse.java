package com.keepit.user.dto;

/** [로그인 임시] 토큰 발급 대신 등록된 회원 프로필만 돌려준다. */
public record ProfileResponse(Long userId, String nickname, String phone, String profileImageKey) {
}
