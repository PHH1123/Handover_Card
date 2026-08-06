package com.handovercard.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 음성의 저장소.
 *
 * <p>저장 위치를 {@code java.nio.file.Path}가 아니라 {@link Resource}로 다룬다. S3처럼 파일시스템이
 * 아닌 저장소에는 대응하는 Path가 없기 때문이며, 전사에 쓰는 Spring AI도 Resource를 받는다.
 * 주고받는 식별자는 상대 경로(또는 객체 키) 문자열이라 저장 방식이 바뀌어도 카드에 담긴 값은 그대로다.
 */
public interface AudioStorageService {

    StoredAudio store(MultipartFile file, Long cardId);

    /** 저장된 음성을 읽기 위한 리소스. 실제 존재 여부는 {@link Resource#exists()}로 확인한다. */
    Resource resolve(String relativePath);

    void delete(String relativePath);
}
