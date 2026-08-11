package com.handovercard.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3ClientConfigTest {

    private final S3ClientConfig config = new S3ClientConfig();

    private static StorageProperties props(String bucket, String region) {
        return new StorageProperties("./data/audio",
                new StorageProperties.S3(bucket, region, "http://localhost:9000", "key", "secret", true));
    }

    /**
     * 환경변수를 주입하지 않으면 바인딩이 플레이스홀더를 문자열 그대로 넘긴다. 빈 값이 아니라서
     * 예전에는 기동이 그대로 됐고, 첫 업로드에 가서야 터졌다.
     */
    @Test
    void 버킷이_미해결_플레이스홀더면_기동에서_막는다() {
        assertThatThrownBy(() -> config.s3Client(props("${S3_BUCKET}", "us-east-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_BUCKET");
    }

    @Test
    void 리전이_미해결_플레이스홀더면_기동에서_막는다() {
        assertThatThrownBy(() -> config.s3Client(props("handover-audio", "${S3_REGION}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3_REGION");
    }

    @Test
    void 버킷이_비면_기동에서_막는다() {
        assertThatThrownBy(() -> config.s3Client(props("  ", "us-east-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("handover.storage.s3.bucket");
    }

    @Test
    void 제대로_채워진_설정은_통과한다() {
        assertThatCode(() -> config.s3Client(props("handover-audio", "us-east-1")))
                .doesNotThrowAnyException();
    }
}
