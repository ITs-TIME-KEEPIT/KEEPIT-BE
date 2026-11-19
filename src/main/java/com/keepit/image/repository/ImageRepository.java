package com.keepit.image.repository;

import com.keepit.image.entity.Image;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageRepository extends JpaRepository<Image, Long> {

	/**
	 * 업로더 본인의 미연결 이미지만 연결 상태로 바꾸고, 바뀐 행 수를 돌려준다.
	 * 호출하는 쪽은 돌려받은 수가 요청 id 수와 같은지 확인한다(다르면 없는 이미지·남의 이미지·이미 연결된 이미지가 섞인 것).
	 * 조건을 WHERE에 두어 같은 이미지를 두 번 연결하지 못하게 하고(InnoDB 행 잠금), 호출하는 트랜잭션 안에서 실행해야 한다.
	 * bulk update는 Auditing을 거치지 않으므로 updated_at도 직접 넣는다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Image i
			set i.linkedAt = :now, i.updatedAt = :now
			where i.id in :ids
			  and i.linkedAt is null
			  and i.uploader.id = :uploaderId
			""")
	int link(@Param("ids") Collection<Long> ids, @Param("uploaderId") Long uploaderId,
			@Param("now") LocalDateTime now);

}
