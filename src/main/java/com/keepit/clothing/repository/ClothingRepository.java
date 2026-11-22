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
	 * 홈 목록(커서 방식). 카드에 쓰는 지역을 함께 가져와(N+1 방지) 최신 등록순(id 내림차순)으로 준다.
	 * category가 null이면 전체, cursor가 null이면 처음부터, 아니면 cursor보다 작은 id부터 준다.
	 * 전체 개수는 세지 않고(Slice) 다음 묶음 여부만 확인한다. Pageable은 첫 페이지(크기만)로 넘긴다.
	 * TODO: 데이터가 늘면 (status, category, id) 커서 조회용 인덱스 검토 (마이그레이션 필요)
	 */
	@Query("""
			select c from Clothing c
			join fetch c.region
			where c.status = :status
			  and c.deletedAt is null
			  and (:category is null or c.category = :category)
			  and (:cursor is null or c.id < :cursor)
			order by c.id desc
			""")
	Slice<Clothing> findList(@Param("status") ClothingStatus status, @Param("category") ClothingCategory category,
			@Param("cursor") Long cursor, Pageable pageable);

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
