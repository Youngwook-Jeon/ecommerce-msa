package com.project.young.productservice.domain;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotSavedException;
import com.project.young.productservice.domain.mapper.ProductDataMapper;
import com.project.young.productservice.domain.ports.output.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ProductCreateCommandHandler {

    private final ProductDataMapper productDataMapper;
    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;

    public ProductCreateCommandHandler(ProductDataMapper productDataMapper, ProductRepository productRepository, ProductDomainService productDomainService) {
        this.productDataMapper = productDataMapper;
        this.productRepository = productRepository;
        this.productDomainService = productDomainService;
    }

    @Transactional
    public ProductCreatedEvent createProduct(CreateProductCommand createProductCommand) {
        Product product = productDataMapper.createProductCommandToProduct(createProductCommand);
        ProductCreatedEvent productCreatedEvent = productDomainService.initiateProduct(product);
        try {
            Product savedProduct = productRepository.createProduct(product);
            log.info("Product saved successfully with id: {}", savedProduct.getId());
        } catch (Exception e) {
            log.error("Could not save product with name: {}", createProductCommand.getProductName(), e);
            throw new ProductNotSavedException("Could not save product with name " +
                    createProductCommand.getProductName() + " caused by " + e.getMessage());
        }
        return productCreatedEvent;
    }
}
