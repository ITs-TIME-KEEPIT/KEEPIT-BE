package com.keepit.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * review_keywords 복합 PK (review_id, keyword).
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ReviewKeywordId implements Serializable {

	@Column(name = "review_id")
	private Long reviewId;

	@Column(nullable = false, length = 30)
	private String keyword;

}
