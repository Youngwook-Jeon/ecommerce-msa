package com.project.young.productservice.domain;

import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
public class ProductDomainServiceImpl implements ProductDomainService {

    @Override
    public ProductCreatedEvent initiateProduct(Product product) {
        log.info("Product [{}] with id: {} is initiated at {}",
                product.getProductName(), product.getId(), ZonedDateTime.now(ZoneId.of("UTC")));

        return new ProductCreatedEvent(product, Instant.now());
    }
}
