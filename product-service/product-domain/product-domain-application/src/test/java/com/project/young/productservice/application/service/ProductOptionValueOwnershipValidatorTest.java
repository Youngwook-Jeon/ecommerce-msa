package com.project.young.productservice.application.service;

import com.project.young.productservice.application.port.output.ProductOptionValueQueryPort;
import com.project.young.productservice.domain.exception.ProductDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionValueOwnershipValidatorTest {

    @Mock
    private ProductOptionValueQueryPort productOptionValueQueryPort;

    @InjectMocks
    private ProductOptionValueOwnershipValidator validator;

    @Test
    @DisplayName("소유 관계가 확인되면 예외 없이 통과한다")
    void requireOwnedByProduct_passesWhenOwned() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        when(productOptionValueQueryPort.existsOwnedByProduct(productId, povId)).thenReturn(true);

        assertThatCode(() -> validator.requireOwnedByProduct(productId, povId))
                .doesNotThrowAnyException();

        verify(productOptionValueQueryPort).existsOwnedByProduct(productId, povId);
    }

    @Test
    @DisplayName("productId가 null이면 예외를 던진다")
    void requireOwnedByProduct_throwsWhenProductIdNull() {
        UUID povId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.requireOwnedByProduct(null, povId))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("productOptionValueId가 null이면 예외를 던진다")
    void requireOwnedByProduct_throwsWhenPovIdNull() {
        UUID productId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.requireOwnedByProduct(productId, null))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("다른 상품의 옵션 값이면 예외를 던진다")
    void requireOwnedByProduct_throwsWhenNotOwned() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        when(productOptionValueQueryPort.existsOwnedByProduct(productId, povId)).thenReturn(false);

        assertThatThrownBy(() -> validator.requireOwnedByProduct(productId, povId))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("does not belong");
    }
}
