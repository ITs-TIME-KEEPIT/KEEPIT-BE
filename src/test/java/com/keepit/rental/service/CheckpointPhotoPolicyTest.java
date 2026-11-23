package com.keepit.rental.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.clothing.entity.PhotoPart;
import com.keepit.rental.entity.ActorType;
import com.keepit.rental.entity.RentalPhotoStage;
import com.keepit.rental.entity.RentalStatus;
import com.keepit.rental.exception.RentalErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CheckpointPhotoPolicyTest {

	@Test
	void 전면_후면_라벨이면_통과하고_디테일은_더해도_된다() {
		assertThatCode(() -> CheckpointPhotoPolicy.validateParts(
				List.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL))).doesNotThrowAnyException();
		assertThatCode(() -> CheckpointPhotoPolicy.validateParts(
				List.of(PhotoPart.LABEL, PhotoPart.FRONT, PhotoPart.DETAIL, PhotoPart.BACK))).doesNotThrowAnyException();
	}

	@Test
	void 필수_부위가_빠지면_막는다() {
		assertError(() -> CheckpointPhotoPolicy.validateParts(List.of(PhotoPart.FRONT, PhotoPart.BACK)),
				RentalErrorCode.CHECKPOINT_PHOTO_PART_MISSING);
	}

	@Test
	void 같은_부위가_두_번이면_막는다() {
		assertError(() -> CheckpointPhotoPolicy.validateParts(
						List.of(PhotoPart.FRONT, PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL)),
				RentalErrorCode.CHECKPOINT_PHOTO_PART_DUPLICATED);
	}

	@Test
	void 흠집_부위는_체크포인트에_쓰지_않는다() {
		assertError(() -> CheckpointPhotoPolicy.validateParts(
						List.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL, PhotoPart.DEFECT)),
				RentalErrorCode.CHECKPOINT_PHOTO_PART_NOT_ALLOWED);
	}

	@Test
	void 단계마다_찍는_사람과_상태가_정해져_있다() {
		assertThat(CheckpointPhotoPolicy.photographerOf(RentalPhotoStage.HANDOVER)).isEqualTo(ActorType.OWNER);
		assertThat(CheckpointPhotoPolicy.statusFor(RentalPhotoStage.HANDOVER)).isEqualTo(RentalStatus.ACCEPTED);
		assertThat(CheckpointPhotoPolicy.photographerOf(RentalPhotoStage.RECEIVE)).isEqualTo(ActorType.BORROWER);
		assertThat(CheckpointPhotoPolicy.statusFor(RentalPhotoStage.RECEIVE)).isEqualTo(RentalStatus.HANDED_OVER);
		assertThat(CheckpointPhotoPolicy.photographerOf(RentalPhotoStage.RETURN)).isEqualTo(ActorType.BORROWER);
		assertThat(CheckpointPhotoPolicy.statusFor(RentalPhotoStage.RETURN)).isEqualTo(RentalStatus.RECEIVED);
	}

	@Test
	void 세탁_후_사진은_따로_올리는_단계가_아니다() {
		assertError(() -> CheckpointPhotoPolicy.photographerOf(RentalPhotoStage.RETURN_WASHED),
				RentalErrorCode.CHECKPOINT_STAGE_NOT_UPLOADABLE);
	}

	@Test
	void 저장된_사진에_필수_부위가_다_있어야_다음_단계로_간다() {
		assertThat(CheckpointPhotoPolicy.isComplete(List.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL))).isTrue();
		assertThat(CheckpointPhotoPolicy.isComplete(List.of(PhotoPart.FRONT, PhotoPart.BACK))).isFalse();
		assertThat(CheckpointPhotoPolicy.isComplete(List.of())).isFalse();
	}

	private static void assertError(Runnable action, RentalErrorCode expected) {
		assertThatThrownBy(action::run).extracting("responseCode").isEqualTo(expected);
	}

}
