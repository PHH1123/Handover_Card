package com.handovercard.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

class S3AudioStorageServiceTest {

    private static final String BUCKET = "handover-audio";

    private S3Client s3Client;
    private S3AudioStorageService service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        StorageProperties props = new StorageProperties("./data/audio",
                new StorageProperties.S3(BUCKET, "us-east-1", "http://localhost:9000", "key", "secret", true));
        service = new S3AudioStorageService(s3Client, props);
    }

    private MockMultipartFile audio(String filename) {
        return new MockMultipartFile("audio", filename, "audio/wav", "fake-audio".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storeUploadsUnderAKeyDerivedFromTheCard() {
        StoredAudio stored = service.store(audio("note.wav"), 42L);

        var request = forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).startsWith("42_").endsWith(".wav");

        // 카드에 저장되는 값은 로컬 구현체와 같은 형식이라 그대로 객체 키가 된다
        assertThat(stored.relativePath()).isEqualTo(request.getValue().key());
        assertThat(stored.originalFilename()).isEqualTo("note.wav");
        assertThat(stored.sizeBytes()).isEqualTo(10L);
    }

    @Test
    void storeRejectsUnsupportedExtensions() {
        assertThatThrownBy(() -> service.store(audio("note.txt"), 1L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("txt");
    }

    @Test
    void storeRejectsFilesWithoutAnExtension() {
        assertThatThrownBy(() -> service.store(audio("note"), 1L))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void storeRejectsAnEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("audio", "note.wav", "audio/wav", new byte[0]);

        assertThatThrownBy(() -> service.store(empty, 1L))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void resolvedResourceReportsWhetherTheObjectExists() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength(10L).build());

        Resource resource = service.resolve("42_abc.wav");

        assertThat(resource.exists()).isTrue();
        // 전사 서비스가 파일 이름을 읽으므로 키에서 뽑아낼 수 있어야 한다
        assertThat(resource.getFilename()).isEqualTo("42_abc.wav");
    }

    @Test
    void resolvedResourceIsMissingWhenTheObjectIsGone() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("gone").build());

        assertThat(service.resolve("42_abc.wav").exists()).isFalse();
    }

    @Test
    void resolvedResourceStreamsTheObject() throws Exception {
        var body = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8))));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(body);

        try (var stream = service.resolve("42_abc.wav").getInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hello");
        }
    }

    @Test
    void deleteRemovesTheObject() {
        service.delete("42_abc.wav");

        var request = forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo("42_abc.wav");
    }

    @Test
    void deleteIgnoresANullPath() {
        service.delete(null);

        verify(s3Client, org.mockito.Mockito.never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
