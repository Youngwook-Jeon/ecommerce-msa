package com.project.young.productservice.dataaccess.config;

import com.project.young.productservice.dataaccess.repository.PublicProductKeywordSearchStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public catalog search tuning. Default keyword strategy is {@link PublicProductKeywordSearchStrategy#NAME_BRAND}.
 */
@ConfigurationProperties(prefix = "product-service.public-search")
public record PublicProductSearchProperties(KeywordSearch keywordSearch) {

    public PublicProductSearchProperties {
        if (keywordSearch == null) {
            keywordSearch = new KeywordSearch(PublicProductKeywordSearchStrategy.NAME_BRAND);
        }
    }

    public PublicProductKeywordSearchStrategy resolvedKeywordStrategy() {
        return keywordSearch.strategy();
    }

    public record KeywordSearch(PublicProductKeywordSearchStrategy strategy) {
        public KeywordSearch {
            if (strategy == null) {
                strategy = PublicProductKeywordSearchStrategy.NAME_BRAND;
            }
        }
    }
}
