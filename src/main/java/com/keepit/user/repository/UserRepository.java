package com.keepit.user.repository;

import com.keepit.user.entity.User;
import com.keepit.user.entity.UserRole;
import com.keepit.user.entity.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	// UNIQUE 인덱스에 원자적으로 쓰기 잠금을 잡아 동시 최초 로그인도 같은 회원 행으로 직렬화한다.
	@Modifying
	@Query(value = """
			INSERT INTO users (kakao_id, role, status, created_at, updated_at)
			VALUES (:kakaoId, 'USER', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			ON DUPLICATE KEY UPDATE id = id
			""", nativeQuery = true)
	void ensureUserAndLock(@Param("kakaoId") String kakaoId);

	// 중복 프로필 제출 중 한 요청만 완료 상태로 전환하도록 직렬화한다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select u from User u where u.id = :id")
	Optional<User> lockById(@Param("id") Long id);

	// 필터에는 회원 식별값, 권한, 상태 및 프로필 완료 여부만 전달한다.
	interface AccountSession {
		Long getMemberId();
		UserRole getRole();
		UserStatus getStatus();
		Long getSessionId();
		int getProfileComplete();
	}

	// MySQL ICU의 공백 집합을 Java String.isBlank의 Character.isWhitespace와 맞춘다.
	String NON_BLANK = "[^\\u0009-\\u000D\\u001C-\\u0020\\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]";

	default Optional<AccountSession> findAccountSession(long id) {
		return selectAccountSession(id, NON_BLANK);
	}

	@Query(value = """
			select u.id as memberId, u.role as role, u.status as status, t.id as sessionId,
			       case when regexp_like(u.nickname, :nonBlank) and regexp_like(u.phone, :nonBlank)
			            then 1 else 0 end as profileComplete
			from users u left join refresh_tokens t on t.user_id = u.id
			where u.id = :id
			""", nativeQuery = true)
	Optional<AccountSession> selectAccountSession(@Param("id") long id, @Param("nonBlank") String nonBlank);

	Optional<User> findByKakaoId(String kakaoId);

}
