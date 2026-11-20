package com.keepit.user.repository;

import com.keepit.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	// 사용자당 보관한 한 건을 조회해서 로그인 시 교체한다.
	Optional<RefreshToken> findByUserId(Long userId);

}
