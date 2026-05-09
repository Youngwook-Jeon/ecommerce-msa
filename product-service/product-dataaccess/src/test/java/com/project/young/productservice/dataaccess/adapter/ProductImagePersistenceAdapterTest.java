package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductImagePersistencePort;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductImagePersistenceAdapterTest {

    @Mock
    private ProductImageJpaRepository productImageJpaRepository;

    @Mock
    private ProductJpaRepository productJpaRepository;

    @InjectMocks
    private ProductImagePersistenceAdapter adapter;

    @Test
    @DisplayName("insert: ACTIVE 상태로 ProductImageEntity를 저장하고 id를 반환한다")
    void insert_SavesEntityAndReturnsId() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        ProductEntity product = ProductEntity.builder().id(productId).build();

        when(productJpaRepository.getReferenceById(productId)).thenReturn(product);
        when(productImageJpaRepository.save(org.mockito.ArgumentMatchers.any(ProductImageEntity.class)))
                .thenAnswer(invocation -> {
                    ProductImageEntity entity = invocation.getArgument(0);
                    entity.setId(imageId);
                    return entity;
                });

        UUID savedId = adapter.insert(
                productId,
                "products/a.jpg",
                "https://pub/a.jpg",
                ProductImageRole.MAIN,
                0,
                "image/jpeg",
                1024L
        );

        assertThat(savedId).isEqualTo(imageId);
        ArgumentCaptor<ProductImageEntity> captor = ArgumentCaptor.forClass(ProductImageEntity.class);
        verify(productImageJpaRepository).save(captor.capture());
        ProductImageEntity persisted = captor.getValue();
        assertThat(persisted.getProduct()).isEqualTo(product);
        assertThat(persisted.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
        assertThat(persisted.getStatus()).isEqualTo(OptionStatusEntity.ACTIVE);
    }

    @Test
    @DisplayName("findByStorageKeyAndProductId: ACTIVE만 조회 결과로 노출한다")
    void findByStorageKeyAndProductId_ReturnsOnlyActive() {
        UUID productId = UUID.randomUUID();
        String storageKey = "products/x.jpg";
        ProductImageEntity active = ProductImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .publicUrl("https://pub/x.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(2)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductImageEntity deleted = ProductImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .publicUrl("https://pub/x.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(2)
                .status(OptionStatusEntity.DELETED)
                .build();

        when(productImageJpaRepository.findByStorageKeyAndProduct_Id(storageKey, productId))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.of(deleted));

        Optional<ProductImagePersistencePort.ProductImageRow> foundActive =
                adapter.findByStorageKeyAndProductId(storageKey, productId);
        Optional<ProductImagePersistencePort.ProductImageRow> foundDeleted =
                adapter.findByStorageKeyAndProductId(storageKey, productId);

        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().role()).isEqualTo(ProductImageRole.GALLERY);
        assertThat(foundDeleted).isEmpty();
    }

    @Test
    @DisplayName("findAllActiveByProductId: MAIN을 우선 정렬하고 sortOrder로 정렬한다")
    void findAllActiveByProductId_SortsMainFirstThenSortOrder() {
        UUID productId = UUID.randomUUID();
        ProductImageEntity galleryEarly = ProductImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey("products/g1.jpg")
                .publicUrl("https://pub/g1.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(0)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductImageEntity main = ProductImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey("products/m.jpg")
                .publicUrl("https://pub/m.jpg")
                .role(ProductImageRoleEntity.MAIN)
                .sortOrder(5)
                .status(OptionStatusEntity.ACTIVE)
                .build();

        when(productImageJpaRepository.findByProduct_IdAndStatusOrderBySortOrderAsc(
                productId, OptionStatusEntity.ACTIVE
        )).thenReturn(List.of(galleryEarly, main));

        List<ProductImagePersistencePort.ProductImageRow> rows = adapter.findAllActiveByProductId(productId);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().role()).isEqualTo(ProductImageRole.MAIN);
        assertThat(rows.get(1).role()).isEqualTo(ProductImageRole.GALLERY);
    }

    @Test
    @DisplayName("updateRole: 갱신 건수가 0이면 예외를 던진다")
    void updateRole_ThrowsWhenNoRowUpdated() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(productImageJpaRepository.updateRole(
                imageId,
                productId,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        )).thenReturn(0);

        assertThatThrownBy(() -> adapter.updateRole(imageId, productId, ProductImageRole.MAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to update image role");
    }
}
