package com.project.young.productservice.dataaccess.mapper;

import com.project.young.productservice.application.support.ProductSearchBrandNormalizer;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.search.OpenSearchProductDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds a complete search projection from a persisted product and its loaded category. */
@Component
public class OpenSearchProductDocumentMapper {

    public OpenSearchProductDocument toDocument(ProductEntity product) {
        require(product != null, "product is required");
        require(product.getId() != null, "product.id is required");
        var category = product.getCategory();
        require(category != null, "product.category is required");
        require(category.getId() != null && category.getId() > 0, "category.id must be positive");
        require(category.getName() != null && !category.getName().isBlank(), "category.name is required");
        require(product.getName() != null && !product.getName().isBlank(), "product.name is required");
        require(product.getStatus() != null, "product.status is required");
        require(category.getStatus() != null, "category.status is required");
        require(product.getCreatedAt() != null, "product.createdAt is required");
        require(product.getBasePrice() != null && product.getBasePrice().signum() >= 0,
                "product.basePrice must be nonnegative");
        BigDecimal price;
        try {
            price = product.getBasePrice().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("product.basePrice must be exactly representable with two decimal places", exception);
        }
        String brandLabel = ProductSearchBrandNormalizer.normalizeLabel(product.getBrand());
        String brandKey = ProductSearchBrandNormalizer.normalizeKey(product.getBrand());
        require(brandLabel == null || brandLabel.codePointCount(0, brandLabel.length()) <= 100,
                "product.brand label exceeds 100 code points");
        require(brandKey == null || brandKey.codePointCount(0, brandKey.length()) <= 100,
                "product.brand key exceeds 100 code points");
        return new OpenSearchProductDocument(product.getId().toString(), category.getId(), category.getName(),
                product.getName(), brandLabel, brandKey, brandLabel, product.getDescription(), product.getMainImageUrl(),
                price, product.getStatus().name(), category.getStatus().name(), product.getCreatedAt().toString());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
