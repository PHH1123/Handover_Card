package com.handovercard.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
@ConditionalOnProperty(prefix = "handover.storage", name = "provider", havingValue = "s3")
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        StorageProperties.S3 s3 = props.s3();
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(s3.region()));

        // 엔드포인트가 있으면 MinIO 같은 S3 호환 저장소, 없으면 실제 AWS S3
        if (StringUtils.hasText(s3.endpoint())) {
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        if (s3.pathStyleAccess()) {
            builder.forcePathStyle(true);
        }
        // 키를 비워 두면 SDK 기본 자격증명 체인(인스턴스 역할 등)을 그대로 쓴다
        if (StringUtils.hasText(s3.accessKey()) && StringUtils.hasText(s3.secretKey())) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3.accessKey(), s3.secretKey())));
        }
        return builder.build();
    }
}
