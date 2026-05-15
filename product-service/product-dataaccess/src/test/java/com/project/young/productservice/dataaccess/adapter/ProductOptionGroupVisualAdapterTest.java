package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductOptionGroupJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionGroupVisualAdapterTest {

    @Mock
    private ProductOptionGroupJpaRepository productOptionGroupJpaRepository;

    @InjectMocks
    private ProductOptionGroupVisualAdapter adapter;

    @Test
    @DisplayName("setDrivesVariantImages=true: 기존 visual 플래그를 해제한 뒤 대상 그룹을 설정한다")
    void setDrivesVariantImages_true_clearsOthersThenUpdatesTarget() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();

        when(productOptionGroupJpaRepository.updateDrivesVariantImages(
                productId, pogId, true, OptionStatusEntity.ACTIVE
        )).thenReturn(1);

        adapter.setDrivesVariantImages(productId, pogId, true);

        verify(productOptionGroupJpaRepository).clearVisualFlagsForProduct(productId, OptionStatusEntity.ACTIVE);
        verify(productOptionGroupJpaRepository).updateDrivesVariantImages(
                productId, pogId, true, OptionStatusEntity.ACTIVE
        );
    }

    @Test
    @DisplayName("setDrivesVariantImages=false: clear 없이 대상 그룹만 갱신한다")
    void setDrivesVariantImages_false_updatesTargetOnly() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();

        when(productOptionGroupJpaRepository.updateDrivesVariantImages(
                productId, pogId, false, OptionStatusEntity.ACTIVE
        )).thenReturn(1);

        adapter.setDrivesVariantImages(productId, pogId, false);

        verify(productOptionGroupJpaRepository, never()).clearVisualFlagsForProduct(productId, OptionStatusEntity.ACTIVE);
        verify(productOptionGroupJpaRepository).updateDrivesVariantImages(
                productId, pogId, false, OptionStatusEntity.ACTIVE
        );
    }

    @Test
    @DisplayName("setDrivesVariantImages: 갱신 건수가 0이면 IllegalStateException을 던진다")
    void setDrivesVariantImages_throwsWhenNoRowUpdated() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();

        when(productOptionGroupJpaRepository.updateDrivesVariantImages(
                productId, pogId, true, OptionStatusEntity.ACTIVE
        )).thenReturn(0);

        assertThatThrownBy(() -> adapter.setDrivesVariantImages(productId, pogId, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to update visual option group flag");
    }

    @Test
    @DisplayName("findActiveVisualGroupId: 활성 visual 그룹 id를 반환한다")
    void findActiveVisualGroupId_returnsGroupId() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        ProductOptionGroupEntity visualGroup = ProductOptionGroupEntity.builder()
                .id(pogId)
                .drivesVariantImages(true)
                .build();

        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.of(visualGroup));

        assertThat(adapter.findActiveVisualGroupId(productId)).contains(pogId);
    }

    @Test
    @DisplayName("findActiveVisualGroupId: visual 그룹이 없으면 empty를 반환한다")
    void findActiveVisualGroupId_returnsEmptyWhenMissing() {
        UUID productId = UUID.randomUUID();
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(adapter.findActiveVisualGroupId(productId)).isEmpty();
    }
}
