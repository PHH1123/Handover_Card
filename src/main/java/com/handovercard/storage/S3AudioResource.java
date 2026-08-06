package com.handovercard.storage;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * S3 객체 하나를 Spring {@code Resource}로 노출한다.
 *
 * <p>전사 서비스가 {@code exists()}, {@code getFilename()}, {@code getInputStream()}을 쓰므로
 * 로컬 파일과 같은 방식으로 다룰 수 있어야 한다. {@code InputStreamResource}는 한 번만 읽을 수 있고
 * 파일 이름도 알려주지 않아 대신 쓸 수 없다.
 */
class S3AudioResource extends AbstractResource {

    private final S3Client s3Client;
    private final String bucket;
    private final String key;

    S3AudioResource(S3Client s3Client, String bucket, String key) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
    }

    @Override
    public boolean exists() {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkException e) {
            return false;
        }
    }

    @Override
    public String getFilename() {
        int lastSlash = key.lastIndexOf('/');
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }

    @Override
    public long contentLength() throws IOException {
        try {
            HeadObjectResponse head = s3Client
                    .headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return head.contentLength();
        } catch (SdkException e) {
            throw new IOException("Failed to read S3 object metadata: " + getDescription(), e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException e) {
            throw new IOException("Failed to open S3 object: " + getDescription(), e);
        }
    }

    @Override
    public String getDescription() {
        return "S3 object [s3://" + bucket + "/" + key + "]";
    }
}
