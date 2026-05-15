package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.ProductOptionValueQueryPort;
import com.project.young.productservice.domain.exception.ProductDomainException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductOptionValueOwnershipValidator {

    private final ProductOptionValueQueryPort productOptionValueQueryPort;

    public ProductOptionValueOwnershipValidator(ProductOptionValueQueryPort productOptionValueQueryPort) {
        this.productOptionValueQueryPort = productOptionValueQueryPort;
    }

    public void requireOwnedByProduct(UUID productId, UUID productOptionValueId) {
        if (productId == null || productOptionValueId == null) {
            throw new ProductDomainException("Product and product option value are required.");
        }
        if (!productOptionValueQueryPort.existsOwnedByProduct(productId, productOptionValueId)) {
            throw new ProductDomainException("Product option value does not belong to this product.");
        }
    }
}
