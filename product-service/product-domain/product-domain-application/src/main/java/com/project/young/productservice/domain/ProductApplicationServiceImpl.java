package com.project.young.productservice.domain;

import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.mapper.ProductDataMapper;
import com.project.young.productservice.domain.ports.input.service.ProductApplicationService;
import com.project.young.productservice.domain.ports.output.message.publisher.ProductMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ProductApplicationServiceImpl implements ProductApplicationService {

    private final ProductCreateCommandHandler productCreateCommandHandler;
    private final ProductMessagePublisher productMessagePublisher;
    private final ProductDataMapper productDataMapper;

    public ProductApplicationServiceImpl(ProductCreateCommandHandler productCreateCommandHandler, ProductMessagePublisher productMessagePublisher, ProductDataMapper productDataMapper) {
        this.productCreateCommandHandler = productCreateCommandHandler;
        this.productMessagePublisher = productMessagePublisher;
        this.productDataMapper = productDataMapper;
    }

    @Override
    public CreateProductResponse createProduct(CreateProductCommand createProductCommand) {
        ProductCreatedEvent productCreatedEvent = productCreateCommandHandler.createProduct(createProductCommand);
        productMessagePublisher.publish(productCreatedEvent);

        return productDataMapper.productToCreateProductResponse(productCreatedEvent.getProduct(),
                "Product created");
    }
}
