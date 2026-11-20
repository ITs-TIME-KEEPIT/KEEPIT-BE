package com.keepit.global.config;

import com.keepit.global.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 자격 증명은 DefaultCredentialsProvider 체인을 사용한다.
 * - 로컬: 환경변수(AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY) 또는 ~/.aws/credentials
 * - EC2: 인스턴스에 연결된 IAM Role
 */
@Configuration
@RequiredArgsConstructor
@Profile("dev")
public class S3Config {

	private final S3Properties s3Properties;

	@Bean
	public S3Client s3Client() {
		Assert.hasText(s3Properties.bucket(), "AWS_S3_BUCKET must be set for the dev profile");
		Assert.hasText(s3Properties.region(), "AWS_REGION must be set for the dev profile");
		return S3Client.builder()
				.region(Region.of(s3Properties.region()))
				.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
				.region(Region.of(s3Properties.region()))
				.build();
	}

}
