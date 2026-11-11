package com.keepit.rental.repository;

import com.keepit.clothing.entity.Clothing;
import com.keepit.clothing.entity.ClothingUnavailablePeriod;
import com.keepit.rental.entity.RentalPeriod;
import com.keepit.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 대여 기능이 쓰는 옷·회원 조회.
 * ClothingRepository·UserRepository는 각 담당자가 만들 예정이라, 같은 파일을 만들어 충돌하지 않도록
 * 대여 쪽에 필요한 조회만 EntityManager로 둔다. 해당 Repository가 develop에 들어오면 그쪽으로 옮긴다.
 */
@Repository
@RequiredArgsConstructor
public class RentalSupportRepository {

	private final EntityManager em;

	public Optional<User> findUser(Long userId) {
		return Optional.ofNullable(em.find(User.class, userId));
	}

	public Optional<Clothing> findClothing(Long clothingId) {
		return Optional.ofNullable(em.find(Clothing.class, clothingId));
	}

	/**
	 * 옷 행을 SELECT ... FOR UPDATE로 잠근다. 트랜잭션이 끝날 때까지 같은 옷을 잠그려는 다른 요청은 기다린다.
	 * 같은 옷의 두 신청이 동시에 수락돼 날짜가 겹치는 것을 막는 데 쓴다.
	 */
	public Optional<Clothing> findClothingForUpdate(Long clothingId) {
		return Optional.ofNullable(em.find(Clothing.class, clothingId, LockModeType.PESSIMISTIC_WRITE));
	}

	/** 옷 주인이 대여 불가로 막아 둔 기간. */
	public List<RentalPeriod> findUnavailablePeriods(Long clothingId) {
		return em.createQuery(
						"select p from ClothingUnavailablePeriod p where p.clothing.id = :clothingId",
						ClothingUnavailablePeriod.class)
				.setParameter("clothingId", clothingId)
				.getResultStream()
				.map(p -> new RentalPeriod(p.getStartDate(), p.getEndDate()))
				.toList();
	}

}
