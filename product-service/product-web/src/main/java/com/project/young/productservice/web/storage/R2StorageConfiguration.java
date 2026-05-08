package com.project.young.productservice.web.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(R2StorageProperties.class)
public class R2StorageConfiguration {
}
