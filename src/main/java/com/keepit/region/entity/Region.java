package com.keepit.region.entity;

import com.keepit.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 지역 (시도·시군구). is_open이 true인 지역에서만 의류를 등록할 수 있다.
 */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, length = 10)
	private String sido;

	@Column(nullable = false, length = 20)
	private String sigungu;

	@Column(name = "is_open", nullable = false)
	private boolean open;

	@Builder
	private Region(String sido, String sigungu, boolean open) {
		this.sido = sido;
		this.sigungu = sigungu;
		this.open = open;
	}

}
