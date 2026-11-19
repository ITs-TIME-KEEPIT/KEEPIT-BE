package com.keepit.global.s3;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 실행·테스트용 파일 저장소. build/local-images 아래에 저장한다(AWS 없이 개발하기 위한 임시 구현, 11/10 안건 ③).
 * 배포 프로필(dev·prod 등)에서는 켜지지 않도록 local·test 프로필에서만 등록한다.
 *
 * <p>TODO: /local-images/** 파일 서빙은 아직 없다. getUrl은 상대 경로만 돌려준다.
 */
@Component
@Profile({"local", "test"})
public class LocalFileUploader implements FileUploader {

	static final Path DEFAULT_BASE_DIR = Path.of("build", "local-images");
	static final String URL_PREFIX = "/local-images/";

	private final Path baseDir;

	public LocalFileUploader() {
		this(DEFAULT_BASE_DIR);
	}

	/** 테스트에서 저장 경로를 바꿀 때만 쓴다. */
	LocalFileUploader(Path baseDir) {
		this.baseDir = baseDir.toAbsolutePath().normalize();
	}

	@Override
	public String upload(MultipartFile file, String directory) {
		String key = createKey(directory, file.getOriginalFilename());
		Path target = resolve(key);
		try (InputStream inputStream = file.getInputStream()) {
			Files.createDirectories(target.getParent());
			Files.copy(inputStream, target);
		} catch (IOException e) {
			throw new UncheckedIOException("로컬 파일 저장에 실패했습니다.", e);
		}
		return key;
	}

	@Override
	public String getUrl(String key) {
		return URL_PREFIX + key;
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		} catch (IOException e) {
			throw new UncheckedIOException("로컬 파일 삭제에 실패했습니다.", e);
		}
	}

	private String createKey(String directory, String originalFilename) {
		String extension = StringUtils.getFilenameExtension(originalFilename);
		String filename = UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extension : "");
		return directory + "/" + filename;
	}

	private Path resolve(String key) {
		Path path = baseDir.resolve(key).normalize();
		if (!path.startsWith(baseDir)) {
			throw new IllegalArgumentException("저장 경로를 벗어난 key입니다: " + key);
		}
		return path;
	}

}
