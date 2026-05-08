package com.project.young.productservice.web.storage;

import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "r2", name = "enabled", havingValue = "true")
public class R2ProductImageStorageAdapter implements ProductImageStoragePort {

    private final R2StorageProperties properties;
    private final S3Presigner presigner;

    public R2ProductImageStorageAdapter(R2StorageProperties properties) {
        this.properties = properties;
        if (properties.getEndpoint() == null || properties.getEndpoint().isBlank()) {
            throw new IllegalStateException("r2.endpoint must be set when r2.enabled=true");
        }
        if (properties.getAccessKeyId() == null || properties.getAccessKeyId().isBlank()
                || properties.getSecretAccessKey() == null || properties.getSecretAccessKey().isBlank()) {
            throw new IllegalStateException("r2.access-key-id and r2.secret-access-key must be set when r2.enabled=true");
        }
        if (properties.getBucket() == null || properties.getBucket().isBlank()) {
            throw new IllegalStateException("r2.bucket must be set when r2.enabled=true");
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getAccessKeyId(),
                properties.getSecretAccessKey()
        );
        String regionId = properties.getRegion() == null || properties.getRegion().isBlank()
                ? "auto"
                : properties.getRegion();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(regionId))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build()
                )
                .build();
    }

    @Override
    public String publicUrlForKey(String objectKey) {
        return DisabledProductImageStorageAdapter.joinBaseAndKey(properties.getPublicBaseUrl(), objectKey);
    }

    @Override
    public PresignedPutResult presignPut(String objectKey, String contentType, long contentLength, Duration ttl) {
        Duration effectiveTtl = ttl != null ? ttl : Duration.ofMinutes(5);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(effectiveTtl)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        Map<String, String> headers = new HashMap<>();
        presigned.httpRequest().headers().forEach((name, values) -> {
            List<String> list = values;
            if (list != null && !list.isEmpty()) {
                headers.put(name, list.getFirst());
            }
        });

        return new PresignedPutResult(
                presigned.url().toString(),
                presigned.httpRequest().method().toString(),
                headers,
                Instant.now().plus(effectiveTtl)
        );
    }
}
