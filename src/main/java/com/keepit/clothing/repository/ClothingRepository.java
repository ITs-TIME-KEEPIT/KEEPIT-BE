package com.keepit.clothing.repository;

import com.keepit.clothing.entity.Clothing;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothingRepository extends JpaRepository<Clothing, Long> {

}
