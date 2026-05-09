package com.project.young.productservice.web.storage;

import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DisabledProductImageStorageAdapterTest {

    @Test
    @DisplayName("publicUrlForKey: base URL과 objectKey를 결합한다")
    void publicUrlForKey_JoinsBaseAndKey() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setPublicBaseUrl("https://pub.example.dev");

        DisabledProductImageStorageAdapter adapter = new DisabledProductImageStorageAdapter(properties);

        String url = adapter.publicUrlForKey("products/abc/file.jpg");

        assertThat(url).isEqualTo("https://pub.example.dev/products/abc/file.jpg");
    }

    @Test
    @DisplayName("presignPut: 개발용 고정 형식 URL/헤더/만료시간을 반환한다")
    void presignPut_ReturnsDeterministicResult() {
        R2StorageProperties properties = new R2StorageProperties();
        properties.setPublicBaseUrl("https://pub.example.dev");

        DisabledProductImageStorageAdapter adapter = new DisabledProductImageStorageAdapter(properties);
        Instant before = Instant.now();

        ProductImageStoragePort.PresignedPutResult result = adapter.presignPut(
                "products/abc/hello world.jpg",
                "image/jpeg",
                1024L,
                Duration.ofMinutes(3)
        );

        assertThat(result.httpMethod()).isEqualTo("PUT");
        assertThat(result.headers()).containsEntry("Content-Type", "image/jpeg");
        assertThat(result.uploadUrl())
                .startsWith("https://pub.example.dev/products/abc/hello world.jpg?contentType=")
                .contains("image%2Fjpeg");
        assertThat(result.expiresAt()).isAfter(before.plusSeconds(170));
    }

    @Test
    @DisplayName("joinBaseAndKey: base가 비어있으면 dev.invalid를 사용한다")
    void joinBaseAndKey_UsesDefaultBaseWhenBlank() {
        String joined = DisabledProductImageStorageAdapter.joinBaseAndKey("", "/products/x.jpg");

        assertThat(joined).isEqualTo("https://dev.invalid/products/x.jpg");
    }
}
