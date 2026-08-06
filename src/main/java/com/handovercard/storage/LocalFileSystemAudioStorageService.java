package com.handovercard.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 로컬 디스크 저장소. 테스트와, S3를 띄우기 번거로운 상황을 위한 기본 구현체다. */
@Service
@ConditionalOnProperty(prefix = "handover.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileSystemAudioStorageService implements AudioStorageService {

    private final Path baseDir;

    public LocalFileSystemAudioStorageService(StorageProperties props) {
        this.baseDir = Path.of(props.baseDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new StorageException("Failed to initialize audio storage directory: " + baseDir, e);
        }
    }

    @Override
    public StoredAudio store(MultipartFile file, Long cardId) {
        if (file.isEmpty()) {
            throw new StorageException("Uploaded audio file is empty");
        }

        String storedFilename = AudioFileNames.storedNameFor(file.getOriginalFilename(), cardId);
        Path target = baseDir.resolve(storedFilename).normalize();

        if (!target.getParent().equals(baseDir)) {
            throw new StorageException("Invalid audio file path");
        }

        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new StorageException("Failed to store audio file", e);
        }

        return new StoredAudio(storedFilename, file.getOriginalFilename(), file.getContentType(), file.getSize());
    }

    @Override
    public Resource resolve(String relativePath) {
        return new FileSystemResource(toPath(relativePath));
    }

    @Override
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(toPath(relativePath));
        } catch (IOException e) {
            throw new StorageException("Failed to delete audio file: " + relativePath, e);
        }
    }

    private Path toPath(String relativePath) {
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            throw new StorageException("Invalid audio file path: " + relativePath);
        }
        return target;
    }
}
