package com.keepit.image.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.util.StringUtils;

/**
 * 업로드를 허용하는 이미지 형식(임시: JPEG·PNG).
 * 파일 앞부분의 시그니처(magic bytes)로 실제 형식을 판별하고, 파일명 확장자가 그 형식과 맞는지 확인한다.
 */
public enum ImageFormat {

	JPEG(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, Set.of("jpg", "jpeg")),
	PNG(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, Set.of("png"));

	/** 판별에 필요한 최대 바이트 수. 파일 전체가 아니라 이만큼만 읽는다. */
	public static final int HEADER_LENGTH = 8;

	// PNG 리더는 픽셀을 읽은 뒤 파일 끝의 IEND 청크가 없어도 성공할 수 있다.
	private static final byte[] PNG_END = {0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
	private static final int VALIDATION_RASTER_SIZE = 1024;
	private static final int MAX_SOURCE_SIDE = 32_768;
	private static final long MAX_SOURCE_PIXELS = 200_000_000L;

	private final byte[] signature;
	private final Set<String> extensions;

	ImageFormat(byte[] signature, Set<String> extensions) {
		this.signature = signature;
		this.extensions = extensions;
	}

	/**
	 * 스트림 앞부분만 읽어 형식을 판별한다. 스트림을 닫는 것은 호출하는 쪽의 몫이다.
	 */
	public static Optional<ImageFormat> detect(InputStream inputStream) throws IOException {
		return detect(inputStream.readNBytes(HEADER_LENGTH));
	}

	public static Optional<ImageFormat> detect(byte[] header) {
		return Arrays.stream(values())
				.filter(format -> format.matchesSignature(header))
				.findFirst();
	}

	/**
	 * 파일명 확장자가 이 형식의 확장자인지 확인한다. 대소문자는 무시하고, 확장자가 없으면 false.
	 */
	public boolean matchesExtension(String filename) {
		String extension = StringUtils.getFilenameExtension(filename);
		if (!StringUtils.hasText(extension)) {
			return false;
		}
		return extensions.contains(extension.toLowerCase(Locale.ROOT));
	}

	public boolean canDecode(byte[] content) {
		if (this == PNG && (content.length < PNG_END.length || !Arrays.equals(
				content, content.length - PNG_END.length, content.length, PNG_END, 0, PNG_END.length))) {
			return false;
		}
		var reader = ImageIO.getImageReadersByFormatName(name()).next();
		try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
			reader.setInput(input, true, true);
			// JPEG 리더는 잘린 데이터를 복구해 반환할 수 있으므로 디코딩 경고도 거부한다.
			boolean[] warned = {false};
			reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
			int width = reader.getWidth(0);
			int height = reader.getHeight(0);
			if (!supportsDimensions(width, height)) {
				return false;
			}
			int longestSide = Math.max(width, height);
			int sample = 1 + (longestSide - 1) / VALIDATION_RASTER_SIZE;
			var params = reader.getDefaultReadParam();
			params.setSourceSubsampling(sample, sample, 0, 0);
			reader.read(0, params);
			return !warned[0];
		} catch (IOException invalidImage) {
			return false;
		} finally {
			reader.dispose();
		}
	}

	static boolean supportsDimensions(int width, int height) {
		return width > 0 && width <= MAX_SOURCE_SIDE && height > 0 && height <= MAX_SOURCE_SIDE
				&& (long) width * height <= MAX_SOURCE_PIXELS;
	}

	private boolean matchesSignature(byte[] header) {
		if (header == null || header.length < signature.length) {
			return false;
		}
		return Arrays.equals(header, 0, signature.length, signature, 0, signature.length);
	}

}
