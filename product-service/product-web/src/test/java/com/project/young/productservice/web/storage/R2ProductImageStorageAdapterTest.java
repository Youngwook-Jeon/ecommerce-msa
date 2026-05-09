package com.project.young.productservice.web.storage;

import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2ProductImageStorageAdapterTest {

    @Test
    @DisplayName("생성자: endpoint 누락 시 예외를 던진다")
    void constructor_ThrowsWhenEndpointMissing() {
        R2StorageProperties properties = baseProperties();
        properties.setEndpoint("");

        assertThatThrownBy(() -> new R2ProductImageStorageAdapter(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("r2.endpoint must be set");
    }

    @Test
    @DisplayName("생성자: access key/secret 누락 시 예외를 던진다")
    void constructor_ThrowsWhenCredentialsMissing() {
        R2StorageProperties properties = baseProperties();
        properties.setAccessKeyId("");

        assertThatThrownBy(() -> new R2ProductImageStorageAdapter(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("r2.access-key-id");
    }

    @Test
    @DisplayName("publicUrlForKey: public-base-url와 objectKey를 결합한다")
    void publicUrlForKey_JoinsPublicBaseAndObjectKey() {
        R2StorageProperties properties = baseProperties();
        R2ProductImageStorageAdapter adapter = new R2ProductImageStorageAdapter(properties);

        String url = adapter.publicUrlForKey("products/1/a.jpg");

        assertThat(url).isEqualTo("https://pub-bucket.r2.dev/products/1/a.jpg");
    }

    @Test
    @DisplayName("presignPut: PUT URL/메서드/만료시간을 반환한다")
    void presignPut_ReturnsSignedPutRequest() {
        R2StorageProperties properties = baseProperties();
        R2ProductImageStorageAdapter adapter = new R2ProductImageStorageAdapter(properties);
        Instant before = Instant.now();

        ProductImageStoragePort.PresignedPutResult result = adapter.presignPut(
                "products/1/2026/05/a.jpg",
                "image/jpeg",
                2048L,
                Duration.ofMinutes(5)
        );

        assertThat(result.uploadUrl()).contains("cloudflarestorage.com");
        assertThat(result.httpMethod()).isEqualTo("PUT");
        assertThat(result.expiresAt()).isAfter(before.plusSeconds(280));
        assertThat(result.headers()).isNotNull();
    }

    private R2StorageProperties baseProperties() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setEnabled(true);
        properties.setEndpoint("https://accountid.r2.cloudflarestorage.com");
        properties.setRegion("auto");
        properties.setAccessKeyId("dummy-access-key");
        properties.setSecretAccessKey("dummy-secret-key");
        properties.setBucket("eco-bucket");
        properties.setPublicBaseUrl("https://pub-bucket.r2.dev");
        return properties;
    }
}
