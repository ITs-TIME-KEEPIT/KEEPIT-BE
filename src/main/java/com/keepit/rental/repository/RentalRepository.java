package com.keepit.rental.repository;

import com.keepit.rental.entity.Rental;
import com.keepit.rental.entity.RentalStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRepository extends JpaRepository<Rental, Long> {

	/** 같은 옷의 특정 상태 대여 건. 일정 검증(예약 상태)과 수락 시 무효 처리(신청 상태)에 쓴다. */
	List<Rental> findAllByClothingIdAndStatusIn(Long clothingId, Collection<RentalStatus> statuses);

}
