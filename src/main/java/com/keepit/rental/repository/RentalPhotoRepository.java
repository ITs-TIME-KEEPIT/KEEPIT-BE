package com.keepit.rental.repository;

import com.keepit.clothing.entity.PhotoPart;
import com.keepit.rental.entity.RentalPhoto;
import com.keepit.rental.entity.RentalPhotoStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalPhotoRepository extends JpaRepository<RentalPhoto, Long> {

	/** 한 거래의 체크포인트 사진 전체. 단계 안에서는 올린 순서(sortOrder) 순. */
	@Query("""
			select p from RentalPhoto p
			where p.rental.id = :rentalId
			order by p.stage, p.sortOrder, p.id
			""")
	List<RentalPhoto> findAllOfRental(@Param("rentalId") Long rentalId);

	/** 한 단계에 저장된 사진 부위. 상태 전이 전에 필수 부위가 다 있는지 볼 때 쓴다. */
	@Query("""
			select p.photoPart from RentalPhoto p
			where p.rental.id = :rentalId
			  and p.stage = :stage
			""")
	List<PhotoPart> findPartsOfStage(@Param("rentalId") Long rentalId, @Param("stage") RentalPhotoStage stage);

	/** 한 단계 사진을 지운다. 같은 단계를 다시 올리면 교체하려고 쓴다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			delete from RentalPhoto p
			where p.rental.id = :rentalId
			  and p.stage = :stage
			""")
	int deleteStage(@Param("rentalId") Long rentalId, @Param("stage") RentalPhotoStage stage);

}
