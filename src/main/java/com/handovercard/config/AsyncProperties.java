package com.handovercard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "handover.async")
public record AsyncProperties(
        int corePoolSize,
        int maxPoolSize,
        int queueCapacity
) {
}
