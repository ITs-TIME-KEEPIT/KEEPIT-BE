package com.keepit.clothing.repository;

import com.keepit.clothing.entity.ClothingColor;
import com.keepit.clothing.entity.ClothingColorId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothingColorRepository extends JpaRepository<ClothingColor, ClothingColorId> {

}
