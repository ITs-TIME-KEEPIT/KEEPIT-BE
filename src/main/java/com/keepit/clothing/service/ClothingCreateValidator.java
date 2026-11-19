package com.keepit.clothing.service;

import com.keepit.clothing.dto.ClothingCreateRequest;
import com.keepit.clothing.dto.ClothingPhotoRequest;
import com.keepit.clothing.dto.MeasurementRequest;
import com.keepit.clothing.dto.UnavailablePeriodRequest;
import com.keepit.clothing.entity.ClothingCategory;
import com.keepit.clothing.entity.PhotoPart;
import com.keepit.clothing.exception.ClothingErrorCode;
import com.keepit.clothing.exception.ClothingException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.util.StringUtils;

/**
 * 옷 등록의 필드 간 규칙(Bean Validation으로 표현하기 어려운 규칙)과, 저장 전에 값을 맞추는 규칙.
 * DB를 쓰지 않으므로 지역·이미지 소유 확인은 ClothingService가 한다.
 */
public final class ClothingCreateValidator {

	/** 일반 사진 + 하자 사진 합계 상한 (임시, 기획 확인 대기). 사진 장수 상수는 여기에만 둔다. */
	public static final int MAX_PHOTO_COUNT = 10;

	/** 일반(하자 외) 사진 최소 장수. */
	public static final int MIN_GENERAL_PHOTO_COUNT = 3;

	private static final Set<PhotoPart> REQUIRED_PARTS = EnumSet.of(PhotoPart.FRONT, PhotoPart.BACK, PhotoPart.LABEL);

	private static final Map<ClothingCategory, Set<Measurement>> ALLOWED_MEASUREMENTS = new EnumMap<>(Map.of(
			ClothingCategory.TOP, EnumSet.of(Measurement.TOTAL_LENGTH, Measurement.SHOULDER_WIDTH,
					Measurement.CHEST_WIDTH, Measurement.SLEEVE_LENGTH),
			ClothingCategory.OUTER, EnumSet.of(Measurement.TOTAL_LENGTH, Measurement.SHOULDER_WIDTH,
					Measurement.CHEST_WIDTH, Measurement.SLEEVE_LENGTH),
			ClothingCategory.BOTTOM, EnumSet.of(Measurement.TOTAL_LENGTH, Measurement.WAIST_WIDTH,
					Measurement.HIP_WIDTH, Measurement.THIGH_WIDTH, Measurement.HEM_WIDTH),
			ClothingCategory.DRESS, EnumSet.of(Measurement.TOTAL_LENGTH, Measurement.SHOULDER_WIDTH,
					Measurement.CHEST_WIDTH, Measurement.WAIST_WIDTH),
			ClothingCategory.ACC, EnumSet.noneOf(Measurement.class)));

	private static final Map<ClothingCategory, Set<Measurement>> REQUIRED_MEASUREMENTS = new EnumMap<>(Map.of(
			ClothingCategory.TOP, EnumSet.of(Measurement.TOTAL_LENGTH),
			ClothingCategory.OUTER, EnumSet.of(Measurement.TOTAL_LENGTH),
			ClothingCategory.BOTTOM, EnumSet.of(Measurement.TOTAL_LENGTH, Measurement.WAIST_WIDTH),
			ClothingCategory.DRESS, EnumSet.of(Measurement.TOTAL_LENGTH),
			ClothingCategory.ACC, EnumSet.noneOf(Measurement.class)));

	private ClothingCreateValidator() {
	}

	/**
	 * Bean Validation을 통과한 요청에 필드 간 규칙을 검사한다. 어긋나면 ClothingException.
	 *
	 * @param today KST 기준 오늘 (대여 불가 기간 종료일 확인용)
	 */
	public static void validate(ClothingCreateRequest request, LocalDate today) {
		validateSize(request);
		validateMeasurement(request);
		validateColors(request);
		validatePhotos(request);
		validateDefectDescription(request);
		validateDelivery(request);
		validateUnavailablePeriods(request.unavailablePeriodsOrEmpty(), today);
	}

	/**
	 * 카테고리에 없는 실측 항목을 비운다. 잡화(ACC)는 모두 비운다.
	 */
	public static MeasurementRequest allowedMeasurement(ClothingCategory category, MeasurementRequest measurement) {
		Set<Measurement> allowed = ALLOWED_MEASUREMENTS.get(category);
		MeasurementRequest source = measurement == null ? MeasurementRequest.EMPTY : measurement;
		return new MeasurementRequest(
				Measurement.TOTAL_LENGTH.keepIf(allowed, source),
				Measurement.SHOULDER_WIDTH.keepIf(allowed, source),
				Measurement.CHEST_WIDTH.keepIf(allowed, source),
				Measurement.SLEEVE_LENGTH.keepIf(allowed, source),
				Measurement.WAIST_WIDTH.keepIf(allowed, source),
				Measurement.HIP_WIDTH.keepIf(allowed, source),
				Measurement.THIGH_WIDTH.keepIf(allowed, source),
				Measurement.HEM_WIDTH.keepIf(allowed, source));
	}

	/**
	 * 실제로 연결할 사진. 하자 없음이면 하자 사진은 무시한다(연결하지 않고 장수에도 세지 않음).
	 */
	public static List<ClothingPhotoRequest> photosToLink(ClothingCreateRequest request) {
		if (request.hasDefect()) {
			return request.photos();
		}
		return request.photos().stream()
				.filter(photo -> photo.photoPart() != PhotoPart.DEFECT)
				.toList();
	}

	private static void validateSize(ClothingCreateRequest request) {
		if (request.category() != ClothingCategory.ACC && request.size() == null) {
			throw new ClothingException(ClothingErrorCode.SIZE_REQUIRED);
		}
	}

	private static void validateMeasurement(ClothingCreateRequest request) {
		MeasurementRequest measurement = request.measurementOrEmpty();
		boolean missing = REQUIRED_MEASUREMENTS.get(request.category()).stream()
				.anyMatch(field -> field.valueOf(measurement) == null);
		if (missing) {
			throw new ClothingException(ClothingErrorCode.REQUIRED_MEASUREMENT_MISSING);
		}
	}

	private static void validateColors(ClothingCreateRequest request) {
		if (new HashSet<>(request.colors()).size() != request.colors().size()) {
			throw new ClothingException(ClothingErrorCode.DUPLICATE_COLOR);
		}
	}

	private static void validatePhotos(ClothingCreateRequest request) {
		List<ClothingPhotoRequest> photos = request.photos();
		long distinctImages = photos.stream().map(ClothingPhotoRequest::imageId).distinct().count();
		if (distinctImages != photos.size()) {
			throw new ClothingException(ClothingErrorCode.DUPLICATE_IMAGE);
		}

		List<ClothingPhotoRequest> general = photos.stream()
				.filter(photo -> photo.photoPart() != PhotoPart.DEFECT)
				.toList();
		Set<PhotoPart> generalParts = EnumSet.noneOf(PhotoPart.class);
		general.forEach(photo -> generalParts.add(photo.photoPart()));
		if (!generalParts.containsAll(REQUIRED_PARTS)) {
			throw new ClothingException(ClothingErrorCode.REQUIRED_PHOTO_PART_MISSING);
		}

		long defectCount = request.hasDefect() ? photos.size() - general.size() : 0;
		if (request.hasDefect() && defectCount == 0) {
			throw new ClothingException(ClothingErrorCode.DEFECT_PHOTO_REQUIRED);
		}
		if (general.size() < MIN_GENERAL_PHOTO_COUNT || general.size() + defectCount > MAX_PHOTO_COUNT) {
			throw new ClothingException(ClothingErrorCode.INVALID_PHOTO_COUNT);
		}
	}

	private static void validateDefectDescription(ClothingCreateRequest request) {
		if (request.hasDefect() && !StringUtils.hasText(request.defectDescription())) {
			throw new ClothingException(ClothingErrorCode.DEFECT_DESCRIPTION_REQUIRED);
		}
	}

	private static void validateDelivery(ClothingCreateRequest request) {
		if (!request.deliveryAvailable()) {
			return;
		}
		if (request.deliveryFee() == null || request.deliveryFee() < 0) {
			throw new ClothingException(ClothingErrorCode.DELIVERY_FEE_REQUIRED);
		}
	}

	private static void validateUnavailablePeriods(List<UnavailablePeriodRequest> periods, LocalDate today) {
		// TODO: 기간끼리 겹침, 종료일 없는 기간 처리 (기획 확인 후)
		for (UnavailablePeriodRequest period : periods) {
			if (period.startDate().isAfter(period.endDate()) || period.endDate().isBefore(today)) {
				throw new ClothingException(ClothingErrorCode.INVALID_UNAVAILABLE_PERIOD);
			}
		}
	}

	private enum Measurement {
		TOTAL_LENGTH(MeasurementRequest::totalLength),
		SHOULDER_WIDTH(MeasurementRequest::shoulderWidth),
		CHEST_WIDTH(MeasurementRequest::chestWidth),
		SLEEVE_LENGTH(MeasurementRequest::sleeveLength),
		WAIST_WIDTH(MeasurementRequest::waistWidth),
		HIP_WIDTH(MeasurementRequest::hipWidth),
		THIGH_WIDTH(MeasurementRequest::thighWidth),
		HEM_WIDTH(MeasurementRequest::hemWidth);

		private final Function<MeasurementRequest, BigDecimal> getter;

		Measurement(Function<MeasurementRequest, BigDecimal> getter) {
			this.getter = getter;
		}

		BigDecimal valueOf(MeasurementRequest measurement) {
			return getter.apply(measurement);
		}

		BigDecimal keepIf(Set<Measurement> allowed, MeasurementRequest measurement) {
			return allowed.contains(this) ? valueOf(measurement) : null;
		}
	}

}
