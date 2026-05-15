package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionValueImagePersistenceAdapterTest {

    @Mock
    private ProductOptionValueImageJpaRepository imageJpaRepository;

    @Mock
    private ProductOptionValueJpaRepository productOptionValueJpaRepository;

    @InjectMocks
    private ProductOptionValueImagePersistenceAdapter adapter;

    @Test
    @DisplayName("insert: ACTIVE 상태로 ProductOptionValueImageEntity를 저장하고 id를 반환한다")
    void insert_savesEntityAndReturnsId() {
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        ProductOptionValueEntity pov = ProductOptionValueEntity.builder().id(povId).build();

        when(productOptionValueJpaRepository.getReferenceById(povId)).thenReturn(pov);
        when(imageJpaRepository.save(any(ProductOptionValueImageEntity.class)))
                .thenAnswer(invocation -> {
                    ProductOptionValueImageEntity entity = invocation.getArgument(0);
                    entity.setId(imageId);
                    return entity;
                });

        UUID savedId = adapter.insert(
                povId,
                "products/pov/a.jpg",
                "https://pub/a.jpg",
                ProductImageRole.MAIN,
                0,
                "image/jpeg",
                1024L
        );

        assertThat(savedId).isEqualTo(imageId);
        ArgumentCaptor<ProductOptionValueImageEntity> captor =
                ArgumentCaptor.forClass(ProductOptionValueImageEntity.class);
        verify(imageJpaRepository).save(captor.capture());
        ProductOptionValueImageEntity persisted = captor.getValue();
        assertThat(persisted.getProductOptionValue()).isEqualTo(pov);
        assertThat(persisted.getRole()).isEqualTo(ProductImageRoleEntity.MAIN);
        assertThat(persisted.getStatus()).isEqualTo(OptionStatusEntity.ACTIVE);
    }

    @Test
    @DisplayName("findByStorageKeyAndProductOptionValueId: ACTIVE만 조회 결과로 노출한다")
    void findByStorageKeyAndProductOptionValueId_returnsOnlyActive() {
        UUID povId = UUID.randomUUID();
        String storageKey = "products/pov/x.jpg";
        ProductOptionValueImageEntity active = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .publicUrl("https://pub/x.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(2)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueImageEntity deleted = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey(storageKey)
                .publicUrl("https://pub/x.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(2)
                .status(OptionStatusEntity.DELETED)
                .build();

        when(imageJpaRepository.findByStorageKeyAndProductOptionValue_Id(storageKey, povId))
                .thenReturn(Optional.of(active))
                .thenReturn(Optional.of(deleted));

        Optional<ProductOptionValueImagePersistencePort.ProductImageRow> foundActive =
                adapter.findByStorageKeyAndProductOptionValueId(storageKey, povId);
        Optional<ProductOptionValueImagePersistencePort.ProductImageRow> foundDeleted =
                adapter.findByStorageKeyAndProductOptionValueId(storageKey, povId);

        assertThat(foundActive).isPresent();
        assertThat(foundActive.get().role()).isEqualTo(ProductImageRole.GALLERY);
        assertThat(foundDeleted).isEmpty();
    }

    @Test
    @DisplayName("findAllActiveByProductOptionValueId: MAIN을 우선 정렬하고 sortOrder로 정렬한다")
    void findAllActiveByProductOptionValueId_sortsMainFirstThenSortOrder() {
        UUID povId = UUID.randomUUID();
        ProductOptionValueImageEntity galleryEarly = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey("products/g1.jpg")
                .publicUrl("https://pub/g1.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(0)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueImageEntity main = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .storageKey("products/m.jpg")
                .publicUrl("https://pub/m.jpg")
                .role(ProductImageRoleEntity.MAIN)
                .sortOrder(5)
                .status(OptionStatusEntity.ACTIVE)
                .build();

        when(imageJpaRepository.findByProductOptionValue_IdAndStatusOrderBySortOrderAsc(
                povId, OptionStatusEntity.ACTIVE
        )).thenReturn(List.of(galleryEarly, main));

        List<ProductOptionValueImagePersistencePort.ProductImageRow> rows =
                adapter.findAllActiveByProductOptionValueId(povId);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().role()).isEqualTo(ProductImageRole.MAIN);
        assertThat(rows.get(1).role()).isEqualTo(ProductImageRole.GALLERY);
    }

    @Test
    @DisplayName("findAllActiveByProductOptionValueIds: 빈 입력이면 빈 Map을 반환한다")
    void findAllActiveByProductOptionValueIds_returnsEmptyMapForEmptyInput() {
        assertThat(adapter.findAllActiveByProductOptionValueIds(List.of())).isEmpty();
        assertThat(adapter.findAllActiveByProductOptionValueIds(null)).isEmpty();
    }

    @Test
    @DisplayName("findAllActiveByProductOptionValueIds: POV별로 이미지를 그룹핑한다")
    void findAllActiveByProductOptionValueIds_groupsByPovId() {
        UUID pov1 = UUID.randomUUID();
        UUID pov2 = UUID.randomUUID();
        ProductOptionValueEntity povEntity1 = ProductOptionValueEntity.builder().id(pov1).build();
        ProductOptionValueEntity povEntity2 = ProductOptionValueEntity.builder().id(pov2).build();

        ProductOptionValueImageEntity image1 = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .productOptionValue(povEntity1)
                .storageKey("products/pov1/a.jpg")
                .publicUrl("https://pub/pov1/a.jpg")
                .role(ProductImageRoleEntity.MAIN)
                .sortOrder(0)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueImageEntity image2 = ProductOptionValueImageEntity.builder()
                .id(UUID.randomUUID())
                .productOptionValue(povEntity2)
                .storageKey("products/pov2/a.jpg")
                .publicUrl("https://pub/pov2/a.jpg")
                .role(ProductImageRoleEntity.GALLERY)
                .sortOrder(1)
                .status(OptionStatusEntity.ACTIVE)
                .build();

        when(imageJpaRepository.findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(
                List.of(pov1, pov2),
                OptionStatusEntity.ACTIVE
        )).thenReturn(List.of(image2, image1));

        Map<UUID, List<ProductOptionValueImagePersistencePort.ProductImageRow>> grouped =
                adapter.findAllActiveByProductOptionValueIds(List.of(pov1, pov2));

        assertThat(grouped).containsKeys(pov1, pov2);
        assertThat(grouped.get(pov1)).hasSize(1);
        assertThat(grouped.get(pov1).getFirst().role()).isEqualTo(ProductImageRole.MAIN);
        assertThat(grouped.get(pov2)).hasSize(1);
        assertThat(grouped.get(pov2).getFirst().role()).isEqualTo(ProductImageRole.GALLERY);
    }

    @Test
    @DisplayName("updateRole: 갱신 건수가 0이면 예외를 던진다")
    void updateRole_throwsWhenNoRowUpdated() {
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(imageJpaRepository.updateRole(
                imageId,
                povId,
                ProductImageRoleEntity.MAIN,
                OptionStatusEntity.ACTIVE
        )).thenReturn(0);

        assertThatThrownBy(() -> adapter.updateRole(imageId, povId, ProductImageRole.MAIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to update option value image role");
    }

    @Test
    @DisplayName("softDelete: repository에 ACTIVE→DELETED soft delete를 위임한다")
    void softDelete_delegatesToRepository() {
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(imageJpaRepository.softDelete(
                imageId,
                povId,
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        )).thenReturn(1);

        int deleted = adapter.softDelete(imageId, povId);

        assertThat(deleted).isEqualTo(1);
        verify(imageJpaRepository).softDelete(
                imageId,
                povId,
                OptionStatusEntity.ACTIVE,
                OptionStatusEntity.DELETED
        );
    }

    @Test
    @DisplayName("updateSortOrder: ACTIVE 조건으로 정렬 인덱스를 갱신한다")
    void updateSortOrder_delegatesToRepository() {
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(imageJpaRepository.updateSortOrder(
                imageId,
                povId,
                4,
                OptionStatusEntity.ACTIVE
        )).thenReturn(1);

        int updated = adapter.updateSortOrder(imageId, povId, 4);

        assertThat(updated).isEqualTo(1);
        verify(imageJpaRepository).updateSortOrder(imageId, povId, 4, OptionStatusEntity.ACTIVE);
    }
}
