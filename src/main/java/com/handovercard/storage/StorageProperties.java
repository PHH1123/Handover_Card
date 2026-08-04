package com.handovercard.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "handover.storage")
public record StorageProperties(
        String baseDir
) {
}
