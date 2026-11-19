package com.keepit.image.support;

/**
 * [로그인 전 임시] 업로드·옷 등록에 쓰는 사용자 id (11/10 회의 결정).
 * 로컬에서 실제로 업로드하려면 DB에 id 1인 사용자를 직접 넣어 두어야 한다.
 *
 * <p>TODO: 로그인 병합 후 JWT 식별로 교체 ({@code @AuthenticationPrincipal Long userId}).
 */
public final class TemporaryUser {

	public static final Long ID = 1L;

	private TemporaryUser() {
	}

}
