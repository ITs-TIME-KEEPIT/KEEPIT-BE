package com.keepit.rental.service;

import com.keepit.clothing.entity.PhotoPart;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.exception.RentalErrorCode;
import com.keepit.rental.exception.RentalException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 체크포인트 사진 규칙 (TRD-510·520·530, Figma "E. 거래 진행 · 사진 체크포인트" 기준).
 * <ul>
 *   <li>단계마다 누가, 어떤 상태에서 찍는지: 전달 직전(주인, 수락됨) · 수령(대여자, 전달됨) · 반납(대여자, 수령됨)</li>
 *   <li>부위: 전면·후면·라벨 필수, 디테일 선택, 부위마다 한 장</li>
 *   <li>반납은 한 번만 찍는다. 대여자가 세탁하는 거래면 반납 사진이 곧 세탁 후 사진이라 RETURN_WASHED는 따로 받지 않는다.</li>
 * </ul>
 */
public final class CheckpointPhotoPolicy {

	public static final Set<PhotoPart> REQUIRED_PARTS = EnumSet.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL);
	public static final Set<PhotoPart> ALLOWED_PARTS = EnumSet.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL,
			PhotoPart.DETAIL);

	private CheckpointPhotoPolicy() {
	}

	/** 이 단계 사진을 올릴 수 있는 거래 상태. 사진을 올리고 나면 이 상태에서 다음 단계로 넘어간다. */
	public static RentalStatus statusFor(RentalPhotoStage stage) {
		return switch (stage) {
			case HANDOVER -> RentalStatus.ACCEPTED;
			case RECEIVE -> RentalStatus.HANDED_OVER;
			case RETURN -> RentalStatus.RECEIVED;
			case RETURN_WASHED -> throw new RentalException(RentalErrorCode.CHECKPOINT_STAGE_NOT_UPLOADABLE);
		};
	}

	/** 이 단계 사진을 찍는 사람. */
	public static ActorType photographerOf(RentalPhotoStage stage) {
		return switch (stage) {
			case HANDOVER -> ActorType.OWNER;
			case RECEIVE, RETURN -> ActorType.BORROWER;
			case RETURN_WASHED -> throw new RentalException(RentalErrorCode.CHECKPOINT_STAGE_NOT_UPLOADABLE);
		};
	}

	/** 올리려는 사진들의 부위를 검사한다: 허용 부위인지 → 같은 부위가 두 번 없는지 → 필수 부위가 다 있는지. */
	public static void validateParts(List<PhotoPart> parts) {
		// 체크포인트에 쓰지 않는 부위(흠집 DEFECT)는 받지 않는다
		if (!ALLOWED_PARTS.containsAll(parts)) {
			throw new RentalException(RentalErrorCode.CHECKPOINT_PHOTO_PART_NOT_ALLOWED);
		}
		// 비교 화면이 단계·부위마다 한 장을 보여 주므로 같은 부위는 한 장만
		if (EnumSet.copyOf(parts).size() != parts.size()) {
			throw new RentalException(RentalErrorCode.CHECKPOINT_PHOTO_PART_DUPLICATED);
		}
		// 전면·후면·라벨은 꼭 있어야 한다
		if (!parts.containsAll(REQUIRED_PARTS)) {
			throw new RentalException(RentalErrorCode.CHECKPOINT_PHOTO_PART_MISSING);
		}
	}

	/** 이미 저장된 사진 부위로 다음 단계로 넘어갈 수 있는지. */
	public static boolean isComplete(Collection<PhotoPart> savedParts) {
		return savedParts.containsAll(REQUIRED_PARTS);
	}

}
