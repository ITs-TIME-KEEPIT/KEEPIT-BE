// [AWS 보류] AWS 세팅은 추후 진행 - 사용 시 아래 주석 해제 (build.gradle AWS SDK 의존성, application.yml aws 설정 포함)
// package com.keepit.global.s3;
//
// import java.io.IOException;
// import java.io.UncheckedIOException;
// import java.util.UUID;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;
// import org.springframework.util.StringUtils;
// import org.springframework.web.multipart.MultipartFile;
// import software.amazon.awssdk.core.sync.RequestBody;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
// import software.amazon.awssdk.services.s3.model.GetUrlRequest;
// import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//
// @Service
// @RequiredArgsConstructor
// public class S3Service {
//
// 	private final S3Client s3Client;
// 	private final S3Properties s3Properties;
//
// 	/**
// 	 * 파일을 업로드하고 S3 object key를 반환한다.
// 	 */
// 	public String upload(MultipartFile file, String directory) {
// 		String key = createKey(directory, file.getOriginalFilename());
// 		PutObjectRequest request = PutObjectRequest.builder()
// 				.bucket(s3Properties.bucket())
// 				.key(key)
// 				.contentType(file.getContentType())
// 				.build();
// 		try {
// 			s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
// 		} catch (IOException e) {
// 			throw new UncheckedIOException("S3 파일 업로드에 실패했습니다.", e);
// 		}
// 		return key;
// 	}
//
// 	public String getUrl(String key) {
// 		return s3Client.utilities()
// 				.getUrl(GetUrlRequest.builder().bucket(s3Properties.bucket()).key(key).build())
// 				.toString();
// 	}
//
// 	public void delete(String key) {
// 		s3Client.deleteObject(DeleteObjectRequest.builder()
// 				.bucket(s3Properties.bucket())
// 				.key(key)
// 				.build());
// 	}
//
// 	private String createKey(String directory, String originalFilename) {
// 		String extension = StringUtils.getFilenameExtension(originalFilename);
// 		String filename = UUID.randomUUID() + (extension != null ? "." + extension : "");
// 		return directory + "/" + filename;
// 	}
//
// }
