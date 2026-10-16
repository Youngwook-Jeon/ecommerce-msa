package com.project.young.productservice.dataaccess.mapper;

import com.project.young.productservice.application.support.ProductSearchBrandNormalizer;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.search.OpenSearchProductDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Builds a versioned search projection; products without a category remain hidden. */
@Component
public class OpenSearchProductDocumentMapper {

    public OpenSearchProductDocument toDocument(ProductEntity product, long revision) {
        require(product != null, "product is required");
        require(product.getId() != null, "product.id is required");
        require(revision >= 1, "search revision must be positive");
        require(product.getStatus() != null, "product.status is required");
        require(product.getCreatedAt() != null, "product.createdAt is required");
        var category = product.getCategory();
        if (category == null) {
            return new OpenSearchProductDocument(product.getId().toString(), null, null, null, null, null, null, null, null,
                    product.getStatus().name(), null, product.getCreatedAt().toString(), revision, false);
        }
        require(category.getId() != null && category.getId() > 0, "category.id must be positive");
        require(product.getName() != null && !product.getName().isBlank(), "product.name is required");
        require(category.getStatus() != null, "category.status is required");
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
        return new OpenSearchProductDocument(product.getId().toString(), category.getId(),
                product.getName(), brandLabel, brandKey, brandLabel, product.getDescription(), product.getMainImageUrl(),
                price, product.getStatus().name(), category.getStatus().name(), product.getCreatedAt().toString(), revision, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
