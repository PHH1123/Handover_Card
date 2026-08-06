package com.handovercard.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "handover.storage")
public record StorageProperties(
        /** 로컬 파일시스템 저장소의 기준 디렉터리. provider=local일 때만 쓰인다. */
        String baseDir,

        S3 s3
) {

    /**
     * S3 호환 저장소 설정.
     *
     * <p>개발 환경에서는 docker-compose의 MinIO를 가리키도록 {@code endpoint}를 지정하고,
     * 운영에서는 비워 두면 실제 AWS S3로 붙는다. 자격증명도 비워 두면 SDK 기본 체인
     * (인스턴스 역할, 환경변수 등)을 따르므로 운영 서버에 키를 심지 않아도 된다.
     */
    public record S3(
            String bucket,
            String region,
            String endpoint,
            String accessKey,
            String secretKey,

            /** MinIO는 가상 호스트 방식 주소를 쓰기 어려워 경로 방식이 필요하다. */
            boolean pathStyleAccess
    ) {
    }
}
