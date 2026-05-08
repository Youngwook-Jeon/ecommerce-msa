package com.project.young.productservice.web.storage;

import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Used when {@code r2.enabled=false} (default): returns deterministic URLs for dev/tests without real uploads.
 */
@Component
@ConditionalOnProperty(prefix = "r2", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledProductImageStorageAdapter implements ProductImageStoragePort {

    private final R2StorageProperties properties;

    public DisabledProductImageStorageAdapter(R2StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String publicUrlForKey(String objectKey) {
        return joinBaseAndKey(properties.getPublicBaseUrl(), objectKey);
    }

    @Override
    public PresignedPutResult presignPut(String objectKey, String contentType, long contentLength, Duration ttl) {
        String url = publicUrlForKey(objectKey) + "?contentType=" + java.net.URLEncoder.encode(contentType, java.nio.charset.StandardCharsets.UTF_8);
        return new PresignedPutResult(
                url,
                "PUT",
                Map.of("Content-Type", contentType),
                Instant.now().plus(ttl != null ? ttl : Duration.ofMinutes(5))
        );
    }

    static String joinBaseAndKey(String base, String objectKey) {
        if (base == null || base.isBlank()) {
            base = "https://dev.invalid";
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String key = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return trimmed + "/" + key;
    }
}
