// [AWS 보류] AWS 세팅은 추후 진행 - 사용 시 아래 주석 해제 (build.gradle AWS SDK 의존성, application.yml aws 설정 포함)
// package com.keepit.global.config;
//
// import com.keepit.global.s3.S3Properties;
// import lombok.RequiredArgsConstructor;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.s3.S3Client;
//
// /**
//  * 자격 증명은 DefaultCredentialsProvider 체인을 사용한다.
//  * - 로컬: 환경변수(AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY) 또는 ~/.aws/credentials
//  * - EC2: 인스턴스에 연결된 IAM Role
//  */
// @Configuration
// @RequiredArgsConstructor
// public class S3Config {
//
// 	private final S3Properties s3Properties;
//
// 	@Bean
// 	public S3Client s3Client() {
// 		return S3Client.builder()
// 				.region(Region.of(s3Properties.region()))
// 				.credentialsProvider(DefaultCredentialsProvider.builder().build())
// 				.build();
// 	}
//
// }
