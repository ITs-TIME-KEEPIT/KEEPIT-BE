package com.keepit.clothing.entity;

import com.keepit.global.entity.BaseTimeEntity;
import com.keepit.region.entity.Region;
import com.keepit.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대여 등록 의류. 실측 치수(cm)는 카테고리에 따라 일부만 입력되므로 nullable.
 * 삭제는 deleted_at으로 soft delete.
 */
@Getter
@Entity
@Table(name = "clothes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothing extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "region_id", nullable = false)
	private Region region;

	@Column(nullable = false, length = 30)
	private String title;

	@Column(length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ClothingCategory category;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ClothingSize size;

	@Column(name = "total_length", precision = 5, scale = 1)
	private BigDecimal totalLength;

	@Column(name = "shoulder_width", precision = 5, scale = 1)
	private BigDecimal shoulderWidth;

	@Column(name = "chest_width", precision = 5, scale = 1)
	private BigDecimal chestWidth;

	@Column(name = "sleeve_length", precision = 5, scale = 1)
	private BigDecimal sleeveLength;

	@Column(name = "waist_width", precision = 5, scale = 1)
	private BigDecimal waistWidth;

	@Column(name = "hip_width", precision = 5, scale = 1)
	private BigDecimal hipWidth;

	@Column(name = "thigh_width", precision = 5, scale = 1)
	private BigDecimal thighWidth;

	@Column(name = "hem_width", precision = 5, scale = 1)
	private BigDecimal hemWidth;

	@Column(name = "has_defect", nullable = false)
	private boolean hasDefect;

	@Column(name = "defect_description", columnDefinition = "TEXT")
	private String defectDescription;

	@Column(name = "rental_fee", nullable = false)
	private int rentalFee;

	@Column(name = "delivery_available", nullable = false)
	private boolean deliveryAvailable;

	@Column(name = "delivery_fee")
	private Integer deliveryFee;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "laundry_by", nullable = false, length = 30)
	private LaundryBy laundryBy;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "laundry_method", nullable = false, length = 30)
	private LaundryMethod laundryMethod;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ClothingStatus status;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Builder
	private Clothing(User owner, Region region, String title, String description,
			ClothingCategory category, ClothingSize size,
			BigDecimal totalLength, BigDecimal shoulderWidth, BigDecimal chestWidth, BigDecimal sleeveLength,
			BigDecimal waistWidth, BigDecimal hipWidth, BigDecimal thighWidth, BigDecimal hemWidth,
			boolean hasDefect, String defectDescription, int rentalFee,
			boolean deliveryAvailable, Integer deliveryFee,
			LaundryBy laundryBy, LaundryMethod laundryMethod) {
		this.owner = owner;
		this.region = region;
		this.title = title;
		this.description = description;
		this.category = category;
		this.size = size;
		this.totalLength = totalLength;
		this.shoulderWidth = shoulderWidth;
		this.chestWidth = chestWidth;
		this.sleeveLength = sleeveLength;
		this.waistWidth = waistWidth;
		this.hipWidth = hipWidth;
		this.thighWidth = thighWidth;
		this.hemWidth = hemWidth;
		this.hasDefect = hasDefect;
		this.defectDescription = defectDescription;
		this.rentalFee = rentalFee;
		this.deliveryAvailable = deliveryAvailable;
		this.deliveryFee = deliveryFee;
		this.laundryBy = laundryBy;
		this.laundryMethod = laundryMethod;
		this.status = ClothingStatus.AVAILABLE;
	}

}
