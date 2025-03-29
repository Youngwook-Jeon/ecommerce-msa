package com.project.young.productservice.domain;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.mapper.ProductDataMapper;
import com.project.young.productservice.domain.ports.output.message.publisher.ProductMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductApplicationServiceImplTest {

    @Mock
    private ProductCreateCommandHandler productCreateCommandHandler;

    @Mock
    private ProductMessagePublisher productMessagePublisher;

    @Mock
    private ProductDataMapper productDataMapper;

    @InjectMocks
    private ProductApplicationServiceImpl productApplicationService;

    private CreateProductCommand createProductCommand;
    private Product product;
    private ProductCreatedEvent productCreatedEvent;

    @BeforeEach
    void setUp() {
        createProductCommand = new CreateProductCommand("Sample Product", "Description", BigDecimal.valueOf(100.0));

        product = Product.builder()
                .productId(new ProductId(UUID.randomUUID()))
                .productName(createProductCommand.getProductName())
                .description(createProductCommand.getDescription())
                .price(new Money((createProductCommand.getPrice())))
                .build();

        productCreatedEvent = new ProductCreatedEvent(product, Instant.now());
    }

    @Test
    void shouldCreateProductSuccessfully() {
        // given
        when(productCreateCommandHandler.createProduct(createProductCommand)).thenReturn(productCreatedEvent);
        when(productDataMapper.productToCreateProductResponse(any(Product.class), anyString()))
                .thenReturn(new CreateProductResponse(product.getProductName(), "Product created"));

        // when
        CreateProductResponse response = productApplicationService.createProduct(createProductCommand);

        // then
        assertNotNull(response);
        assertEquals("Product created", response.message());
        verify(productCreateCommandHandler, times(1)).createProduct(createProductCommand);
        verify(productMessagePublisher, times(1)).publish(productCreatedEvent);
    }
}