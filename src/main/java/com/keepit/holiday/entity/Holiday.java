package com.keepit.holiday.entity;

import com.keepit.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 명절·연휴 기간. delivery_cutoff_date는 택배 접수 마감일.
 */
@Getter
@Entity
@Table(name = "holidays")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "delivery_cutoff_date")
	private LocalDate deliveryCutoffDate;

	@Builder
	private Holiday(String name, LocalDate startDate, LocalDate endDate, LocalDate deliveryCutoffDate) {
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
		this.deliveryCutoffDate = deliveryCutoffDate;
	}

}
