package com.keepit.clothing.entity;

import com.keepit.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소유자가 지정한 대여 불가 기간.
 */
@Getter
@Entity
@Table(name = "clothing_unavailable_periods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothingUnavailablePeriod extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "clothing_id", nullable = false)
	private Clothing clothing;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Builder
	private ClothingUnavailablePeriod(Clothing clothing, LocalDate startDate, LocalDate endDate) {
		this.clothing = clothing;
		this.startDate = startDate;
		this.endDate = endDate;
	}

}
