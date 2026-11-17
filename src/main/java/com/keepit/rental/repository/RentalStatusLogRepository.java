package com.keepit.rental.repository;

import com.keepit.rental.entity.RentalStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalStatusLogRepository extends JpaRepository<RentalStatusLog, Long> {

}
