package com.project.young.productservice.domain;

import com.project.young.common.domain.util.IdentityGenerator;
import com.project.young.common.domain.valueobject.ProductID;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.exception.ProductAlreadyExistsException;
import com.project.young.productservice.domain.mapper.ProductDataMapper;
import com.project.young.productservice.domain.ports.output.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Component
public class ProductCreateCommandHandler {

    private final ProductDataMapper productDataMapper;
    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;
    private final IdentityGenerator<ProductID> identityGenerator;

    public ProductCreateCommandHandler(ProductDataMapper productDataMapper, ProductRepository productRepository, ProductDomainService productDomainService, IdentityGenerator<ProductID> identityGenerator) {
        this.productDataMapper = productDataMapper;
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
        this.identityGenerator = identityGenerator;
    }

    @Transactional
    public ProductCreatedEvent createProduct(CreateProductCommand createProductCommand) {
        checkProduct(createProductCommand.getProductName());
        Product product = productDataMapper.createProductCommandToProduct(createProductCommand);
        product.setId(identityGenerator.generateID());

        productDomainService.validateProduct(product);

        productRepository.createProduct(product);
        log.info("Product [{}] with id: {} is created at {}",
                product.getProductName(), product.getId().getValue(), ZonedDateTime.now(ZoneId.of("UTC")));

        return new ProductCreatedEvent(product, Instant.now());
    }

    private void checkProduct(String productName) {
        productRepository.findByProductName(productName)
                .ifPresent(product -> {
                    throw new ProductAlreadyExistsException("Product with name " + productName + " already exists");
                });
    }
}
