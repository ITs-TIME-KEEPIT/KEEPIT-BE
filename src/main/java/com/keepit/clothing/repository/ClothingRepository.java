package com.keepit.clothing.repository;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingStatus;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothingRepository extends JpaRepository<Clothing, Long> {

	/**
	 * 홈 목록. 카드에 쓰는 지역을 함께 가져와(N+1 방지) 최신 등록순으로 준다. category가 null이면 전체.
	 * 전체 개수는 세지 않고(Slice) 다음 페이지 여부만 확인한다.
	 * TODO: 데이터가 늘면 (status, deleted_at, category, created_at) 조회용 인덱스 검토 (마이그레이션 필요)
	 */
	@Query("""
			select c from Clothing c
			join fetch c.region
			where c.status = :status
			  and c.deletedAt is null
			  and (:category is null or c.category = :category)
			order by c.createdAt desc, c.id desc
			""")
	Slice<Clothing> findList(@Param("status") ClothingStatus status, @Param("category") ClothingCategory category,
			Pageable pageable);

	/**
	 * 상세. 대여 중지(PAUSED) 옷도 주고, 삭제된 옷은 주지 않는다. 지역·등록자를 함께 가져온다.
	 */
	@Query("""
			select c from Clothing c
			join fetch c.region
			join fetch c.owner
			where c.id = :id
			  and c.deletedAt is null
			""")
	Optional<Clothing> findDetail(@Param("id") Long id);

}
