package com.keepit.rental.dto;

import com.keepit.clothing.entity.PhotoPart;
import com.keepit.rental.entity.ActorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 체크포인트 사진 비교 조회 응답 (GET /api/v1/rentals/{rentalId}/checkpoints, TRD-550).
 * 등록 시·전달 직전·수령·반납 4단계를 항상 이 순서로 준다. 아직 안 찍은 단계는 사진이 빈 목록이다.
 */
public record RentalCheckpointResponse(
		@Schema(description = "대여 건 id", example = "1") Long rentalId,
		@Schema(description = "단계별 사진. 항상 REGISTER → HANDOVER → RECEIVE → RETURN 순서 4개")
		List<Stage> stages
) {

	/** 비교 화면의 4단계. 1단계(등록 시)는 옷 등록 사진이다. */
	public enum Step {
		REGISTER, HANDOVER, RECEIVE, RETURN
	}

	public record Stage(
			@Schema(description = "단계. REGISTER=등록 시, HANDOVER=전달 직전, RECEIVE=수령, RETURN=반납", example = "HANDOVER")
			Step step,
			@Schema(description = "찍은 사람. OWNER=등록자, BORROWER=대여자", example = "OWNER")
			ActorType takenBy,
			@Schema(description = "촬영 시각(서버가 사진을 받은 시각 중 가장 이른 것). 사진이 없으면 null",
					example = "2026-11-14T13:52:00")
			LocalDateTime takenAt,
			@Schema(description = "사진 목록. 아직 안 찍었으면 빈 목록")
			List<Photo> photos
	) {
	}

	public record Photo(
			@Schema(description = "촬영 부위. FRONT=전면, BACK=후면, LABEL=라벨, DETAIL=디테일, DEFECT=흠집(등록 사진만)",
					example = "FRONT")
			PhotoPart photoPart,
			@Schema(description = "사진 주소. 응답마다 새로 발급되고 잠시 뒤 만료된다",
					example = "https://keepit-bucket.s3.ap-northeast-2.amazonaws.com/rentals/abc.jpg?X-Amz-...")
			String imageUrl,
			@Schema(description = "서버가 사진을 받은 시각", example = "2026-11-14T13:52:00")
			LocalDateTime serverReceivedAt
	) {
	}

}
