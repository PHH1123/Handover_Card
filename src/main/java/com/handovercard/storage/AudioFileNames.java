package com.handovercard.storage;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 저장 구현체들이 공유하는 파일 이름 규칙. 두 저장소가 같은 형식의 식별자를 만들어야 서로 바꿔 끼울 수 있다. */
final class AudioFileNames {

    // webm/mp4는 브라우저 MediaRecorder의 출력 포맷 (Chrome·Firefox는 webm, Safari는 mp4)
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("mp3", "wav", "m4a", "ogg", "aac", "webm", "mp4");

    private AudioFileNames() {
    }

    /** 업로드된 파일 이름을 검증하고 저장에 쓸 이름(로컬 파일명 · S3 객체 키)을 만든다. */
    static String storedNameFor(String originalFilename, Long cardId) {
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new StorageException("Unsupported audio file extension: " + extension);
        }
        return cardId + "_" + UUID.randomUUID() + "." + extension;
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new StorageException("Audio file must have an extension");
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
