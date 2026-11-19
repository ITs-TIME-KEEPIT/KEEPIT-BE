package com.keepit.global.s3;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 공통 인터페이스. S3Service와 같은 모양(upload/getUrl/delete)을 따른다.
 * - 로컬 실행·테스트: LocalFileUploader
 * - dev(AWS): S3Service가 이 인터페이스를 구현한다 (1번 담당에게 요청, 반영 전까지 dev에는 구현체가 없다)
 *
 * <p>저장·삭제에 실패하면 RuntimeException을 던진다. 응답 형식으로 바꾸는 것은 호출하는 도메인의 몫이다.
 */
public interface FileUploader {

	/**
	 * 파일을 저장하고 객체 key를 반환한다. key 형식: {directory}/{UUID}.{파일명 확장자}
	 */
	String upload(MultipartFile file, String directory);

	/**
	 * key로 응답에 쓸 URL을 만든다. 응답에는 key 대신 항상 이 URL을 쓴다.
	 */
	String getUrl(String key);

	void delete(String key);

}
