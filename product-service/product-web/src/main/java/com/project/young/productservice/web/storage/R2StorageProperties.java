package com.project.young.productservice.web.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "r2")
public class R2StorageProperties {

    /**
     * When false, a no-op presigner is used (local/tests).
     */
    private boolean enabled = false;

    private String endpoint = "";
    private String region = "auto";
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String bucket = "";
    /**
     * Public origin for object URLs (no trailing slash), e.g. https://pub.r2.dev/my-bucket
     */
    private String publicBaseUrl = "https://dev.invalid";
}
