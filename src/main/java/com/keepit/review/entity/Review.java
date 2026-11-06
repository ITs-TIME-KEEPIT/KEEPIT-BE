package com.keepit.review.entity;

import com.keepit.global.entity.BaseTimeEntity;
import com.keepit.rental.entity.Rental;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대여 완료 후 상대방에 대한 리뷰. 대여 1건당 작성자별 1개 (rental_id, writer_id UNIQUE).
 */
@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rental_id", nullable = false)
	private Rental rental;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "writer_id", nullable = false)
	private User writer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_id", nullable = false)
	private User target;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private ReviewSatisfaction satisfaction;

	@Column
	private String comment;

	@Builder
	private Review(Rental rental, User writer, User target, ReviewSatisfaction satisfaction, String comment) {
		this.rental = rental;
		this.writer = writer;
		this.target = target;
		this.satisfaction = satisfaction;
		this.comment = comment;
	}

}
