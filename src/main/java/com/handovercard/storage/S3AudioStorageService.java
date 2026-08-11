package com.handovercard.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * S3 호환 저장소. 개발 환경에서는 docker-compose의 MinIO를, 운영에서는 AWS S3를 바라본다.
 *
 * <p>카드에 저장되는 값은 로컬 구현체와 마찬가지로 상대 경로 문자열이며 여기서는 객체 키로 쓰인다.
 * 두 구현체가 같은 형식을 쓰기 때문에 저장 방식을 바꿔도 이미 저장된 값의 의미가 달라지지 않는다.
 */
@Service
@ConditionalOnProperty(prefix = "handover.storage", name = "provider", havingValue = "s3")
public class S3AudioStorageService implements AudioStorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3AudioStorageService(S3Client s3Client, StorageProperties props) {
        this.s3Client = s3Client;
        this.bucket = props.s3().bucket();
    }

    @Override
    public StoredAudio store(MultipartFile file, Long cardId) {
        if (file.isEmpty()) {
            throw new StorageException("Uploaded audio file is empty");
        }

        String key = AudioFileNames.storedNameFor(file.getOriginalFilename(), cardId);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(file.getContentType())
                .build();

        try {
            // 스트림을 그대로 넘기면 SDK가 재시도할 때 되감지 못해, 원래 실패 원인이
            // "Content input stream does not support mark/reset"라는 다른 예외로 덮인다.
            // 그러면 StorageException도 아니게 되어 저장소 오류로 취급되지 않는다.
            // 공급자를 넘기면 시도할 때마다 새 스트림을 열어 주므로 원인이 그대로 올라온다.
            s3Client.putObject(request, RequestBody.fromContentProvider(
                    () -> openStream(file), file.getSize(), request.contentType()));
        } catch (SdkException | UncheckedIOException e) {
            throw new StorageException("Failed to store audio file", e);
        }

        return new StoredAudio(key, file.getOriginalFilename(), file.getContentType(), file.getSize());
    }

    /** {@link software.amazon.awssdk.core.sync.RequestBody}의 공급자는 검사 예외를 던질 수 없다. */
    private static InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Resource resolve(String relativePath) {
        return new S3AudioResource(s3Client, bucket, relativePath);
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(relativePath).build());
        } catch (SdkException e) {
            throw new StorageException("Failed to delete audio file: " + relativePath, e);
        }
    }

}
