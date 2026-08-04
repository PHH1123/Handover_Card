package com.handovercard.storage;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileSystemAudioStorageService implements AudioStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "m4a", "ogg", "aac");

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

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new StorageException("Unsupported audio file extension: " + extension);
        }

        String storedFilename = cardId + "_" + UUID.randomUUID() + "." + extension;
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
    public Path resolve(String relativePath) {
        return baseDir.resolve(relativePath).normalize();
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new StorageException("Audio file must have an extension");
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext;
    }
}
