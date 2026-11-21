package com.keepit.image.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageFormatTest {

	static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
	static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
	static final byte[] GIF_HEADER = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00};

	@ParameterizedTest
	@CsvSource({
			"32768,1,true", "32769,1,false", "1,32768,true", "1,32769,false",
			"20000,10000,true", "20000,10001,false", "16320,12240,true",
			"0,100,false", "100,0,false", "2147483647,2147483647,false"
	})
	void checksSourceDimensionsBeforeDecoding(int width, int height, boolean supported) {
		assertThat(ImageFormat.supportsDimensions(width, height)).isEqualTo(supported);
	}

	@Test
	@DisplayName("JPEG 시그니처를 JPEG로 판별한다")
	void JPEG_판별() {
		assertThat(ImageFormat.detect(JPEG_HEADER)).contains(ImageFormat.JPEG);
	}

	@Test
	@DisplayName("PNG 시그니처를 PNG로 판별한다")
	void PNG_판별() {
		assertThat(ImageFormat.detect(PNG_HEADER)).contains(ImageFormat.PNG);
	}

	@Test
	@DisplayName("허용하지 않는 형식(GIF)은 판별되지 않는다")
	void 미지원_형식() {
		assertThat(ImageFormat.detect(GIF_HEADER)).isEmpty();
	}

	@Test
	@DisplayName("시그니처보다 짧은 파일은 판별되지 않는다")
	void 짧은_파일() {
		assertThat(ImageFormat.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
		assertThat(ImageFormat.detect(new byte[0])).isEmpty();
	}

	@Test
	@DisplayName("스트림에서는 판별에 필요한 앞부분만 읽는다")
	void 스트림_앞부분만_읽기() throws IOException {
		byte[] content = new byte[1024];
		System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);
		InputStream inputStream = new ByteArrayInputStream(content);

		assertThat(ImageFormat.detect(inputStream)).contains(ImageFormat.PNG);
		assertThat(inputStream.available()).isEqualTo(content.length - ImageFormat.HEADER_LENGTH);
	}

	@ParameterizedTest
	@ValueSource(strings = {"photo.jpg", "photo.jpeg", "photo.JPG", "photo.JpEg", "my.photo.jpg"})
	@DisplayName("JPEG는 jpg·jpeg 확장자를 대소문자 구분 없이 허용한다")
	void JPEG_확장자_허용(String filename) {
		assertThat(ImageFormat.JPEG.matchesExtension(filename)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"photo.png", "photo.PNG"})
	@DisplayName("PNG는 png 확장자를 대소문자 구분 없이 허용한다")
	void PNG_확장자_허용(String filename) {
		assertThat(ImageFormat.PNG.matchesExtension(filename)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {"photo.png", "photo.gif", "photo.jpg.png"})
	@DisplayName("판별 형식과 다른 확장자는 거절한다")
	void 확장자_불일치(String filename) {
		assertThat(ImageFormat.JPEG.matchesExtension(filename)).isFalse();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"photo", "photo."})
	@DisplayName("확장자가 없으면 거절한다")
	void 확장자_없음(String filename) {
		assertThat(ImageFormat.JPEG.matchesExtension(filename)).isFalse();
	}

}
