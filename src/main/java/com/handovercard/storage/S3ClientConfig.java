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

        // bucket은 S3AudioStorageService가 읽지만 검증은 여기서 함께 한다. 그 빈은 이 빈에
        // 의존하므로 여기서 막으면 잘못된 설정으로 업로드가 시도되는 일이 없다.
        require(s3.bucket(), "handover.storage.s3.bucket", "S3_BUCKET");
        require(s3.region(), "handover.storage.s3.region", "S3_REGION");

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

    /**
     * provider=s3일 때 반드시 있어야 하는 값을 기동 시점에 확인한다.
     *
     * <p>빈 값뿐 아니라 {@code ${S3_BUCKET}} 같은 미해결 플레이스홀더도 함께 막는다.
     * {@code @ConfigurationProperties} 바인딩은 풀지 못한 플레이스홀더를 오류로 보지 않고 문자열
     * 그대로 넘기기 때문에, 환경변수를 빠뜨리면 기동은 멀쩡히 되고 첫 업로드에서야
     * "${S3_BUCKET}"이라는 이름의 버킷을 찾다가 터진다. 그래서 빈 값 검사만으로는 부족하다.
     * S3 버킷 이름과 리전에는 {@code ${}}가 들어갈 수 없으므로 오탐할 여지도 없다.
     */
    private static void require(String value, String property, String envVar) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "%s must be set when handover.storage.provider=s3 (환경변수 %s)".formatted(property, envVar));
        }
        if (value.contains("${")) {
            throw new IllegalStateException(
                    "%s이(가) 미해결 플레이스홀더 '%s' 상태다. 환경변수 %s를 주입해야 한다."
                            .formatted(property, value, envVar));
        }
    }
}
