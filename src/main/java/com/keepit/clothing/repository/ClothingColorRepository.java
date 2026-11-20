package com.keepit.clothing.repository;

import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingColorId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothingColorRepository extends JpaRepository<ClothingColor, ClothingColorId> {

	@Query("""
			select c from ClothingColor c
			where c.clothing.id = :clothingId
			order by c.id.color
			""")
	List<ClothingColor> findAllOfClothing(@Param("clothingId") Long clothingId);

}
