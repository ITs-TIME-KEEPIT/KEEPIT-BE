package com.keepit.clothing.repository;

import com.keepit.clothing.entity.ClothingPhoto;
import com.keepit.clothing.entity.PhotoPart;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClothingPhotoRepository extends JpaRepository<ClothingPhoto, Long> {

	/**
	 * 여러 옷의 특정 부위 사진을 한 번에 가져온다(목록 대표 사진, N+1 방지). 옷마다 sort_order, id 순.
	 */
	@Query("""
			select p from ClothingPhoto p
			where p.clothing.id in :clothingIds
			  and p.photoPart = :photoPart
			order by p.clothing.id, p.sortOrder, p.id
			""")
	List<ClothingPhoto> findByClothingIdsAndPart(@Param("clothingIds") Collection<Long> clothingIds,
			@Param("photoPart") PhotoPart photoPart);

}
