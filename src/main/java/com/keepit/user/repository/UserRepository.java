package com.keepit.user.repository;

import com.keepit.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	// 카카오 회원번호의 유일 제약에 따라 하나의 회원으로 가입을 이어간다.
	Optional<User> findByKakaoId(String kakaoId);

}
