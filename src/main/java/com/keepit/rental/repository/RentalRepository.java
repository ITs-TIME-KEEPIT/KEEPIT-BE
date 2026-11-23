package com.keepit.rental.repository;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RentalRepository extends JpaRepository<Rental, Long> {

	/** 같은 옷의 특정 상태 대여 건. 일정 검증(예약 상태)과 수락 시 무효 처리(신청 상태)에 쓴다. */
	List<Rental> findAllByClothingIdAndStatusIn(Long clothingId, Collection<RentalStatus> statuses);

	/** 신청 엔티티를 읽지 않고 옷 id만 꺼낸다. 옷 행을 잠그기 전에 쓴다. */
	@Query("select r.clothing.id from Rental r where r.id = :rentalId")
	Optional<Long> findClothingIdById(@Param("rentalId") Long rentalId);

	/**
	 * 내 옷에 들어온 신청 (RENT-420). 옷·상태는 넘기지 않으면(null) 거르지 않는다.
	 * 옷과 신청자를 한 번에 가져와서(fetch join) 목록을 만들 때 건마다 추가 쿼리가 나가지 않게 한다.
	 */
	@Query("""
			select r from Rental r
			join fetch r.clothing c
			join fetch r.borrower b
			where r.owner.id = :ownerId
			  and (:clothingId is null or c.id = :clothingId)
			  and (:status is null or r.status = :status)
			order by r.requestedAt desc
			""")
	List<Rental> findReceivedRequests(@Param("ownerId") Long ownerId,
			@Param("clothingId") Long clothingId,
			@Param("status") RentalStatus status);

	/**
	 * 내가 빌린 거래 전체 (MYP-601 "빌린 옷" 탭). 최신 신청순.
	 * 카드에 쓰는 옷·지역·상대(옷 주인)를 한 번에 가져와 건마다 추가 쿼리가 나가지 않게 한다.
	 */
	@Query("""
			select r from Rental r
			join fetch r.clothing c
			join fetch c.region
			join fetch r.owner
			where r.borrower.id = :borrowerId
			order by r.requestedAt desc, r.id desc
			""")
	List<Rental> findAllBorrowedBy(@Param("borrowerId") Long borrowerId);

	/** 내가 빌려준 거래 전체 (MYP-601 "빌려준 옷" 탭). 최신 신청순. 상대는 대여자. */
	@Query("""
			select r from Rental r
			join fetch r.clothing c
			join fetch c.region
			join fetch r.borrower
			where r.owner.id = :ownerId
			order by r.requestedAt desc, r.id desc
			""")
	List<Rental> findAllLentBy(@Param("ownerId") Long ownerId);

}
