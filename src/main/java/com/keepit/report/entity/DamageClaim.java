package com.keepit.report.entity;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파손 신고에 대한 손해 청구 (수선·세탁 영수증 기반).
 */
@Getter
@Entity
@Table(name = "damage_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DamageClaim extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "report_id", nullable = false)
	private Report report;

	@Column(name = "receipt_image_key")
	private String receiptImageKey;

	@Column(name = "claim_amount", nullable = false)
	private int claimAmount;

	@Column
	private String result;

	@Builder
	private DamageClaim(Report report, String receiptImageKey, int claimAmount) {
		this.report = report;
		this.receiptImageKey = receiptImageKey;
		this.claimAmount = claimAmount;
	}

}
