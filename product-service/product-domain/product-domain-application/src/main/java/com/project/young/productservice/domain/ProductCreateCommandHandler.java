package com.project.young.productservice.domain;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.exception.ProductAlreadyExistsException;
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
        checkProduct(createProductCommand.getProductName());
        Product product = productDataMapper.createProductCommandToProduct(createProductCommand);
        ProductCreatedEvent productCreatedEvent = productDomainService.initiateProduct(product);
        Product savedProduct = productRepository.createProduct(product);
        log.info("Product saved successfully with id: {}", savedProduct.getId());

        return productCreatedEvent;
    }

    private void checkProduct(String productName) {
        productRepository.findByProductName(productName)
                .ifPresent(product -> {
                    throw new ProductAlreadyExistsException("Product with name " + productName + " already exists");
                });
    }
}
