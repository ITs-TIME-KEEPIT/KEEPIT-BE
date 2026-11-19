package com.keepit.clothing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingPhotoRequest;
import com.keepit.clothing.dto.MeasurementRequest;
import com.keepit.clothing.dto.UnavailablePeriodRequest;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.ClothingSize;
import com.keepit.clothing.entity.Color;
import com.keepit.clothing.entity.LaundryBy;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ClothingCreateValidatorTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

	private static final BigDecimal LEN = new BigDecimal("60.0");

	/** 테스트마다 필요한 값만 바꿔 쓰는 요청 빌더. 기본값은 통과하는 상의 요청. */
	static final class Req {
		String title = "니트";
		ClothingCategory category = ClothingCategory.TOP;
		ClothingSize size = ClothingSize.M;
		List<Color> colors = List.of(Color.NAVY);
		MeasurementRequest measurement = new MeasurementRequest(LEN, null, null, null, null, null, null, null);
		boolean hasDefect = false;
		String defectDescription = null;
		boolean deliveryAvailable = false;
		Integer deliveryFee = null;
		List<UnavailablePeriodRequest> periods = null;
		List<ClothingPhotoRequest> photos = new ArrayList<>(List.of(
				photo(1, PhotoPart.FRONT, 0), photo(2, PhotoPart.BACK, 1), photo(3, PhotoPart.LABEL, 2)));

		ClothingCreateRequest build() {
			return new ClothingCreateRequest(title, null, category, size, colors, measurement, hasDefect,
					defectDescription, 14, 10000, deliveryAvailable, deliveryFee, LaundryBy.BORROWER, null,
					periods, photos);
		}
	}

	static ClothingPhotoRequest photo(long imageId, PhotoPart part, int sortOrder) {
		return new ClothingPhotoRequest(imageId, part, sortOrder);
	}

	private static void assertValid(Req req) {
		assertThatCode(() -> ClothingCreateValidator.validate(req.build(), TODAY)).doesNotThrowAnyException();
	}

	private static void assertError(Req req, ClothingErrorCode expected) {
		assertThatThrownBy(() -> ClothingCreateValidator.validate(req.build(), TODAY))
				.isInstanceOf(ClothingException.class)
				.extracting("responseCode").isEqualTo(expected);
	}

	@Test
	@DisplayName("기본 상의 요청은 통과한다")
	void 기본_요청_통과() {
		assertValid(new Req());
	}

	@Nested
	@DisplayName("사이즈·실측")
	class 사이즈_실측 {

		@Test
		@DisplayName("잡화가 아닌데 사이즈가 없으면 CLOTHING_400_2")
		void 사이즈_필수() {
			Req req = new Req();
			req.size = null;
			assertError(req, ClothingErrorCode.SIZE_REQUIRED);
		}

		@Test
		@DisplayName("잡화는 사이즈와 실측이 없어도 통과한다")
		void 잡화는_사이즈_실측_없음() {
			Req req = new Req();
			req.category = ClothingCategory.ACC;
			req.size = null;
			req.measurement = null;
			assertValid(req);
		}

		@Test
		@DisplayName("상의·아우터·원피스는 총장이 없으면 CLOTHING_400_1")
		void 총장_필수() {
			for (ClothingCategory category : List.of(ClothingCategory.TOP, ClothingCategory.OUTER, ClothingCategory.DRESS)) {
				Req req = new Req();
				req.category = category;
				req.measurement = new MeasurementRequest(null, LEN, LEN, null, null, null, null, null);
				assertError(req, ClothingErrorCode.REQUIRED_MEASUREMENT_MISSING);
			}
		}

		@Test
		@DisplayName("실측 객체 자체가 없으면 CLOTHING_400_1")
		void 실측_없음() {
			Req req = new Req();
			req.measurement = null;
			assertError(req, ClothingErrorCode.REQUIRED_MEASUREMENT_MISSING);
		}

		@Test
		@DisplayName("하의는 총장과 허리단면이 모두 있어야 한다")
		void 하의_총장_허리단면_필수() {
			Req onlyLength = new Req();
			onlyLength.category = ClothingCategory.BOTTOM;
			onlyLength.measurement = new MeasurementRequest(LEN, null, null, null, null, null, null, null);
			assertError(onlyLength, ClothingErrorCode.REQUIRED_MEASUREMENT_MISSING);

			Req both = new Req();
			both.category = ClothingCategory.BOTTOM;
			both.measurement = new MeasurementRequest(LEN, null, null, null, LEN, null, null, null);
			assertValid(both);
		}

		@Test
		@DisplayName("카테고리에 없는 실측은 비우고, 허용된 항목만 남긴다")
		void 허용되지_않은_실측_비우기() {
			MeasurementRequest all = new MeasurementRequest(new BigDecimal("1.0"), new BigDecimal("2.0"),
					new BigDecimal("3.0"), new BigDecimal("4.0"), new BigDecimal("5.0"), new BigDecimal("6.0"),
					new BigDecimal("7.0"), new BigDecimal("8.0"));

			assertThat(ClothingCreateValidator.allowedMeasurement(ClothingCategory.TOP, all))
					.isEqualTo(new MeasurementRequest(all.totalLength(), all.shoulderWidth(), all.chestWidth(),
							all.sleeveLength(), null, null, null, null));
			assertThat(ClothingCreateValidator.allowedMeasurement(ClothingCategory.OUTER, all))
					.isEqualTo(ClothingCreateValidator.allowedMeasurement(ClothingCategory.TOP, all));
			assertThat(ClothingCreateValidator.allowedMeasurement(ClothingCategory.BOTTOM, all))
					.isEqualTo(new MeasurementRequest(all.totalLength(), null, null, null, all.waistWidth(),
							all.hipWidth(), all.thighWidth(), all.hemWidth()));
			assertThat(ClothingCreateValidator.allowedMeasurement(ClothingCategory.DRESS, all))
					.isEqualTo(new MeasurementRequest(all.totalLength(), all.shoulderWidth(), all.chestWidth(),
							null, all.waistWidth(), null, null, null));
			assertThat(ClothingCreateValidator.allowedMeasurement(ClothingCategory.ACC, all))
					.isEqualTo(MeasurementRequest.EMPTY);
		}
	}

	@Test
	@DisplayName("같은 컬러를 두 번 고르면 CLOTHING_400_3")
	void 컬러_중복() {
		Req req = new Req();
		req.colors = List.of(Color.NAVY, Color.NAVY);
		assertError(req, ClothingErrorCode.DUPLICATE_COLOR);
	}

	@Nested
	@DisplayName("사진")
	class 사진 {

		@Test
		@DisplayName("전면·후면·라벨 중 하나라도 없으면 CLOTHING_400_4")
		void 필수_부위_누락() {
			Req req = new Req();
			req.photos = List.of(photo(1, PhotoPart.FRONT, 0), photo(2, PhotoPart.BACK, 1), photo(3, PhotoPart.DETAIL, 2));
			assertError(req, ClothingErrorCode.REQUIRED_PHOTO_PART_MISSING);
		}

		@Test
		@DisplayName("하자 사진은 필수 부위로 치지 않는다")
		void 하자_사진은_필수_부위_아님() {
			Req req = new Req();
			req.hasDefect = true;
			req.defectDescription = "얼룩";
			req.photos = List.of(photo(1, PhotoPart.FRONT, 0), photo(2, PhotoPart.BACK, 1), photo(3, PhotoPart.DEFECT, 2));
			assertError(req, ClothingErrorCode.REQUIRED_PHOTO_PART_MISSING);
		}

		@Test
		@DisplayName("같은 부위 여러 장과 같은 sortOrder는 허용한다")
		void 부위_순서_중복_허용() {
			Req req = new Req();
			req.photos = List.of(photo(1, PhotoPart.FRONT, 0), photo(2, PhotoPart.FRONT, 0),
					photo(3, PhotoPart.BACK, 1), photo(4, PhotoPart.LABEL, 2));
			assertValid(req);
		}

		@Test
		@DisplayName("일반 사진이 열 장을 넘으면 CLOTHING_400_5")
		void 일반_사진_상한_초과() {
			Req req = new Req();
			req.photos = new ArrayList<>(req.photos);
			for (int i = 4; i <= 11; i++) {
				req.photos.add(photo(i, PhotoPart.DETAIL, i));
			}
			assertError(req, ClothingErrorCode.INVALID_PHOTO_COUNT);
		}

		@Test
		@DisplayName("하자 있음이면 일반 + 하자 사진 합계가 열 장까지만 된다")
		void 합계_상한() {
			Req ok = defectReq(7, 3);
			assertValid(ok);

			Req over = defectReq(7, 4);
			assertError(over, ClothingErrorCode.INVALID_PHOTO_COUNT);
		}

		@Test
		@DisplayName("하자 없음이면 하자 사진은 합계에 세지 않는다")
		void 하자_없음이면_하자_사진_제외() {
			Req req = defectReq(10, 3);
			req.hasDefect = false;
			req.defectDescription = null;
			assertValid(req);
			assertThat(ClothingCreateValidator.photosToLink(req.build()))
					.hasSize(10)
					.noneMatch(photo -> photo.photoPart() == PhotoPart.DEFECT);
		}

		@Test
		@DisplayName("하자 있음이면 하자 사진도 연결 대상이다")
		void 하자_있음이면_하자_사진_포함() {
			Req req = defectReq(3, 2);
			assertThat(ClothingCreateValidator.photosToLink(req.build())).hasSize(5);
		}

		@Test
		@DisplayName("하자 있음인데 하자 사진이 없으면 CLOTHING_400_6")
		void 하자_사진_필수() {
			Req req = new Req();
			req.hasDefect = true;
			req.defectDescription = "얼룩";
			assertError(req, ClothingErrorCode.DEFECT_PHOTO_REQUIRED);
		}

		@Test
		@DisplayName("imageId가 겹치면 CLOTHING_400_10 (무시될 하자 사진 포함)")
		void 이미지_중복() {
			Req req = new Req();
			req.photos = new ArrayList<>(req.photos);
			req.photos.add(photo(1, PhotoPart.DEFECT, 3));
			assertError(req, ClothingErrorCode.DUPLICATE_IMAGE);
		}

		/** 전면·후면·라벨 + 디테일로 일반 사진 generalCount장, 하자 사진 defectCount장. */
		private Req defectReq(int generalCount, int defectCount) {
			Req req = new Req();
			req.hasDefect = true;
			req.defectDescription = "소매 올 풀림";
			List<ClothingPhotoRequest> photos = new ArrayList<>(req.photos);
			long id = 100;
			for (int i = 3; i < generalCount; i++) {
				photos.add(photo(id++, PhotoPart.DETAIL, i));
			}
			for (int i = 0; i < defectCount; i++) {
				photos.add(photo(id++, PhotoPart.DEFECT, generalCount + i));
			}
			req.photos = photos;
			return req;
		}
	}

	@Test
	@DisplayName("하자 있음인데 하자 설명이 비어 있으면 CLOTHING_400_7")
	void 하자_설명_필수() {
		Req req = new Req();
		req.hasDefect = true;
		req.defectDescription = "  ";
		req.photos = new ArrayList<>(req.photos);
		req.photos.add(photo(9, PhotoPart.DEFECT, 3));
		assertError(req, ClothingErrorCode.DEFECT_DESCRIPTION_REQUIRED);
	}

	@Nested
	@DisplayName("택배")
	class 택배 {

		@Test
		@DisplayName("택배 가능인데 택배비가 없거나 음수면 CLOTHING_400_8")
		void 택배비_필수() {
			Req missing = new Req();
			missing.deliveryAvailable = true;
			assertError(missing, ClothingErrorCode.DELIVERY_FEE_REQUIRED);

			Req negative = new Req();
			negative.deliveryAvailable = true;
			negative.deliveryFee = -1;
			assertError(negative, ClothingErrorCode.DELIVERY_FEE_REQUIRED);
		}

		@Test
		@DisplayName("택배비 0원은 허용하고, 택배 불가면 택배비를 보지 않는다")
		void 택배비_허용_범위() {
			Req free = new Req();
			free.deliveryAvailable = true;
			free.deliveryFee = 0;
			assertValid(free);

			Req notAvailable = new Req();
			notAvailable.deliveryFee = -5;
			assertValid(notAvailable);
		}
	}

	@Nested
	@DisplayName("대여 불가 기간")
	class 대여_불가_기간 {

		@Test
		@DisplayName("시작일이 종료일보다 뒤면 CLOTHING_400_9")
		void 시작일_종료일_역전() {
			Req req = new Req();
			req.periods = List.of(new UnavailablePeriodRequest(TODAY.plusDays(5), TODAY.plusDays(3)));
			assertError(req, ClothingErrorCode.INVALID_UNAVAILABLE_PERIOD);
		}

		@Test
		@DisplayName("종료일이 오늘 이전이면 CLOTHING_400_9")
		void 지난_기간() {
			Req req = new Req();
			req.periods = List.of(new UnavailablePeriodRequest(TODAY.minusDays(3), TODAY.minusDays(1)));
			assertError(req, ClothingErrorCode.INVALID_UNAVAILABLE_PERIOD);
		}

		@Test
		@DisplayName("하루짜리 기간, 오늘 끝나는 기간, 여러 구간, 기간 없음은 허용한다")
		void 허용되는_기간() {
			Req req = new Req();
			req.periods = List.of(
					new UnavailablePeriodRequest(TODAY, TODAY),
					new UnavailablePeriodRequest(TODAY.minusDays(2), TODAY),
					new UnavailablePeriodRequest(TODAY.plusDays(30), TODAY.plusDays(32)));
			assertValid(req);

			Req none = new Req();
			none.periods = null;
			assertValid(none);
		}
	}

}
