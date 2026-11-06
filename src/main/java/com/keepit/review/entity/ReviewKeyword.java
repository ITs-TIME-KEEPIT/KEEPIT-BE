package com.keepit.review.entity;

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
 * 리뷰 키워드 (리뷰 1개에 여러 키워드). 복합 PK라 Persistable로 신규 여부를 판단한다.
 */
@Getter
@Entity
@Table(name = "review_keywords")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewKeyword extends BaseTimeEntity implements Persistable<ReviewKeywordId> {

	@EmbeddedId
	private ReviewKeywordId id;

	@MapsId("reviewId")
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "review_id", nullable = false)
	private Review review;

	public ReviewKeyword(Review review, String keyword) {
		this.id = new ReviewKeywordId(review.getId(), keyword);
		this.review = review;
	}

	public String getKeyword() {
		return id.getKeyword();
	}

	@Override
	public boolean isNew() {
		return getCreatedAt() == null;
	}

}
