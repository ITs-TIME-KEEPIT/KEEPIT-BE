package com.keepit.clothing.repository;

import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothingUnavailablePeriodRepository extends JpaRepository<ClothingUnavailablePeriod, Long> {

	/**
	 * 등록자가 막아 둔 원본 구간을 시작일 순으로 준다. 계산된 대여 가능 날짜는 대여(3번) availability API가 맡는다.
	 */
	@Query("""
			select p from ClothingUnavailablePeriod p
			where p.clothing.id = :clothingId
			order by p.startDate, p.id
			""")
	List<ClothingUnavailablePeriod> findAllOfClothing(@Param("clothingId") Long clothingId);

}
