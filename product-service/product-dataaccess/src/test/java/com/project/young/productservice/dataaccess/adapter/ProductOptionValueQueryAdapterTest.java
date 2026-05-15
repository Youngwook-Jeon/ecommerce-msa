package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionValueQueryAdapterTest {

    @Mock
    private ProductOptionValueJpaRepository productOptionValueJpaRepository;

    @InjectMocks
    private ProductOptionValueQueryAdapter adapter;

    @Test
    @DisplayName("existsOwnedByProduct: JPA repository에 위임한다")
    void existsOwnedByProduct_delegatesToRepository() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        when(productOptionValueJpaRepository.existsByIdAndProductOptionGroup_Product_Id(povId, productId))
                .thenReturn(true);

        assertThat(adapter.existsOwnedByProduct(productId, povId)).isTrue();

        verify(productOptionValueJpaRepository).existsByIdAndProductOptionGroup_Product_Id(povId, productId);
    }

    @Test
    @DisplayName("findProductIdByProductOptionValueId: JPA repository에 위임한다")
    void findProductIdByProductOptionValueId_delegatesToRepository() {
        UUID povId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(productOptionValueJpaRepository.findProductIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(productId));

        assertThat(adapter.findProductIdByProductOptionValueId(povId)).contains(productId);

        verify(productOptionValueJpaRepository).findProductIdByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("findProductOptionGroupIdByProductOptionValueId: JPA repository에 위임한다")
    void findProductOptionGroupIdByProductOptionValueId_delegatesToRepository() {
        UUID povId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        when(productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(pogId));

        assertThat(adapter.findProductOptionGroupIdByProductOptionValueId(povId)).contains(pogId);

        verify(productOptionValueJpaRepository).findProductOptionGroupIdByProductOptionValueId(povId);
    }
}
