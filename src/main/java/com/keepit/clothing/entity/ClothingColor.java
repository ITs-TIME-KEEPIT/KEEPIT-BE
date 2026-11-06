package com.keepit.clothing.entity;

import com.keepit.global.entity.BaseTimeEntity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 의류 색상 (의류 1개에 여러 색상). 복합 PK라 id가 항상 채워져 있으므로
 * Persistable로 신규 여부를 판단해 save 시 불필요한 select(merge)를 피한다.
 */
@Getter
@Entity
@Table(name = "clothing_colors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothingColor extends BaseTimeEntity implements Persistable<ClothingColorId> {

	@EmbeddedId
	private ClothingColorId id;

	@MapsId("clothingId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "clothing_id", nullable = false)
	private Clothing clothing;

	public ClothingColor(Clothing clothing, Color color) {
		this.id = new ClothingColorId(clothing.getId(), color);
		this.clothing = clothing;
	}

	public Color getColor() {
		return id.getColor();
	}

	@Override
	public boolean isNew() {
		return getCreatedAt() == null;
	}

}
