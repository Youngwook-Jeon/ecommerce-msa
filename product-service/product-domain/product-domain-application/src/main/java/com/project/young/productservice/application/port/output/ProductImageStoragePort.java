package com.project.young.productservice.application.port.output;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public interface ProductImageStoragePort {

    /**
     * Public URL served to browsers for an object key (CDN / R2 public bucket URL).
     */
    String publicUrlForKey(String objectKey);

    /**
     * @return HTTP PUT URL and headers the client must send when uploading bytes to object storage.
     */
    PresignedPutResult presignPut(String objectKey, String contentType, long contentLength, Duration ttl);

    record PresignedPutResult(
            String uploadUrl,
            String httpMethod,
            Map<String, String> headers,
            Instant expiresAt
    ) {
    }
}
