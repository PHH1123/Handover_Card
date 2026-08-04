package com.handovercard.storage;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface AudioStorageService {

    StoredAudio store(MultipartFile file, Long cardId);

    Path resolve(String relativePath);
}
