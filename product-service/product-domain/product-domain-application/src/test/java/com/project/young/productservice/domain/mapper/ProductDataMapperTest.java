package com.project.young.productservice.domain.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.dto.CreateProductResponse;
import com.project.young.productservice.domain.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductDataMapperTest {

    private ProductDataMapper productDataMapper;

    @BeforeEach
    void setUp() {
        productDataMapper = new ProductDataMapper();
    }

    @Test
    void shouldConvertCreateProductCommandToProduct() {
        // given
        CreateProductCommand command = new CreateProductCommand("Sample Product", "This is a test product", BigDecimal.valueOf(100.0));

        // when
        Product product = productDataMapper.createProductCommandToProduct(command);

        // then
        assertNotNull(product);
        assertEquals("Sample Product", product.getProductName());
        assertEquals("This is a test product", product.getDescription());
        assertEquals(new Money(BigDecimal.valueOf(100.0)), product.getPrice());
    }

    @Test
    void shouldConvertProductToCreateProductResponse() {
        // given
        Product product = Product.builder()
                .productName("Sample Product")
                .description("This is a test product")
                .price(new Money(BigDecimal.valueOf(100.0)))
                .build();
        String message = "Product created successfully";

        // when
        CreateProductResponse response = productDataMapper.productToCreateProductResponse(product, message);

        // then
        assertNotNull(response);
        assertEquals("Sample Product", response.productName());
        assertEquals("Product created successfully", response.message());
    }
}