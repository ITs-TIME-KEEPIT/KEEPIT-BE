package com.keepit.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileUploaderTest {

	private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	@TempDir
	Path baseDir;

	private LocalFileUploader uploader;

	@BeforeEach
	void setUp() {
		uploader = new LocalFileUploader(baseDir);
	}

	@Test
	@DisplayName("파일을 저장하고 {directory}/{UUID}.{확장자} 형식의 key를 돌려준다")
	void 업로드_key_형식과_파일_저장() throws IOException {
		byte[] content = {1, 2, 3};
		MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);

		String key = uploader.upload(file, "clothes");

		assertThat(key).matches("clothes/" + UUID_PATTERN + "\\.jpg");
		assertThat(Files.readAllBytes(baseDir.resolve(key))).containsExactly(content);
	}

	@Test
	@DisplayName("같은 파일명을 두 번 올려도 key가 겹치지 않는다")
	void 업로드_key_중복_없음() {
		MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});

		assertThat(uploader.upload(file, "clothes")).isNotEqualTo(uploader.upload(file, "clothes"));
	}

	@Test
	@DisplayName("URL은 /local-images/{key} 상대 경로다")
	void URL_형식() {
		assertThat(uploader.getUrl("clothes/a.jpg")).isEqualTo("/local-images/clothes/a.jpg");
	}

	@Test
	@DisplayName("삭제하면 파일이 사라지고, 없는 key를 지워도 오류가 나지 않는다")
	void 삭제() {
		MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1});
		String key = uploader.upload(file, "clothes");

		uploader.delete(key);
		uploader.delete(key);

		assertThat(baseDir.resolve(key)).doesNotExist();
	}

	@Test
	@DisplayName("저장 경로 밖을 가리키는 key는 거절한다")
	void 경로_이탈_거절() {
		assertThatThrownBy(() -> uploader.delete("../outside.jpg"))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
