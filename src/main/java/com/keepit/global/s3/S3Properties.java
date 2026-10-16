// [AWS 보류] AWS 세팅은 추후 진행 - 사용 시 아래 주석 해제 (build.gradle AWS SDK 의존성, application.yml aws 설정 포함)
// package com.keepit.global.s3;
//
// import org.springframework.boot.context.properties.ConfigurationProperties;
//
// @ConfigurationProperties(prefix = "aws")
// public record S3Properties(String region, S3 s3) {
//
// 	public record S3(String bucket) {
// 	}
//
// 	public String bucket() {
// 		return s3.bucket();
// 	}
//
// }
