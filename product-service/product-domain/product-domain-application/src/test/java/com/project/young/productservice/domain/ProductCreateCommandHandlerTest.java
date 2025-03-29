package com.project.young.productservice.domain;

import com.project.young.common.domain.util.IdentityGenerator;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.domain.dto.CreateProductCommand;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.event.ProductCreatedEvent;
import com.project.young.productservice.domain.exception.ProductAlreadyExistsException;
import com.project.young.productservice.domain.mapper.ProductDataMapper;
import com.project.young.productservice.domain.ports.output.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCreateCommandHandlerTest {

    @Mock
    private ProductDataMapper productDataMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDomainService productDomainService;

    @Mock
    private IdentityGenerator<ProductId> identityGenerator;

    @InjectMocks
    private ProductCreateCommandHandler productCreateCommandHandler;

    private CreateProductCommand createProductCommand;
    private Product product;
    private ProductId productId;

    @BeforeEach
    void setUp() {
        createProductCommand = new CreateProductCommand("Sample Product", "Description", BigDecimal.valueOf(100.0));
        productId = new ProductId(UUID.randomUUID());

        product = Product.builder()
                .productId(productId)
                .productName(createProductCommand.getProductName())
                .description(createProductCommand.getDescription())
                .price(new Money((createProductCommand.getPrice())))
                .build();
    }

    @Test
    void shouldCreateProductSuccessfully() {
        // given
        when(productRepository.findByProductName(createProductCommand.getProductName())).thenReturn(Optional.empty());
        when(productDataMapper.createProductCommandToProduct(createProductCommand)).thenReturn(product);
        when(identityGenerator.generateID()).thenReturn(productId);

        // when
        ProductCreatedEvent result = productCreateCommandHandler.createProduct(createProductCommand);

        // then
        assertNotNull(result);
        assertEquals(product.getId(), result.getProduct().getId());
        assertEquals(product.getProductName(), result.getProduct().getProductName());
        verify(productRepository, times(1)).createProduct(product);
        verify(productDomainService, times(1)).validateProduct(product);
    }

    @Test
    void shouldThrowExceptionWhenProductAlreadyExists() {
        // given
        when(productRepository.findByProductName(createProductCommand.getProductName()))
                .thenReturn(Optional.of(product));

        // when & then
        assertThrows(ProductAlreadyExistsException.class, () -> {
            productCreateCommandHandler.createProduct(createProductCommand);
        });

        verify(productRepository, never()).createProduct(any());
        verify(productDomainService, never()).validateProduct(any());
    }
}