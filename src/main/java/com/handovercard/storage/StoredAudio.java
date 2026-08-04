package com.handovercard.storage;

public record StoredAudio(
        String relativePath,
        String originalFilename,
        String contentType,
        long sizeBytes
) {
}
