package com.keepit.user.repository;

import com.keepit.user.entity.User;
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

	Optional<User> findByKakaoId(String kakaoId);

}
