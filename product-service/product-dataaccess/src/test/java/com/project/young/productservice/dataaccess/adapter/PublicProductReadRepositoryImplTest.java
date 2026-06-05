package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.policy.StorefrontProductVisibilityPolicy;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import com.project.young.productservice.dataaccess.mapper.ProductDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.OptionGroupJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueImageJpaRepository;
import com.project.young.productservice.dataaccess.repository.PublicProductSearchQueryRepository;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProductReadRepositoryImplTest {

    @Mock
    private PublicProductSearchQueryRepository publicProductSearchQueryRepository;
    @Mock
    private ProductJpaRepository productJpaRepository;
    @Mock
    private ProductDataAccessMapper productDataAccessMapper;
    @Mock
    private ProductImageJpaRepository productImageJpaRepository;
    @Mock
    private ProductOptionValueImageJpaRepository productOptionValueImageJpaRepository;
    @Mock
    private OptionGroupJpaRepository optionGroupJpaRepository;

    @InjectMocks
    private PublicProductReadRepositoryImpl publicProductReadRepository;

    @Nested
    @DisplayName("findStorefrontProductDetailById")
    class FindStorefrontProductDetailByIdTests {

        @Test
        @DisplayName("DRAFT/DELETED 제외 상태를 쿼리에 전달")
        void passesExcludedStatuses() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);
            ProductEntity productEntity = ProductEntity.builder()
                    .id(rawId)
                    .name("preview")
                    .description("preview desc long enough")
                    .basePrice(new BigDecimal("15000"))
                    .status(ProductStatusEntity.INACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .mainImageUrl("https://example.com/main.jpg")
                    .build();

            when(productJpaRepository.findStorefrontDetailWithOptionsById(eq(rawId), anyList(), eq(CategoryStatusEntity.DELETED)))
                    .thenReturn(Optional.of(productEntity));
            when(productJpaRepository.findStorefrontDetailWithVariantsById(eq(rawId), anyList(), eq(CategoryStatusEntity.DELETED)))
                    .thenReturn(Optional.of(productEntity));
            when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.INACTIVE)).thenReturn(ProductStatus.INACTIVE);
            when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW)).thenReturn(ConditionType.NEW);
            when(productImageJpaRepository.findByProduct_IdAndStatusOrderBySortOrderAsc(rawId, OptionStatusEntity.ACTIVE))
                    .thenReturn(List.of());

            Optional<ReadProductDetailView> result = publicProductReadRepository.findStorefrontProductDetailById(productId);
            assertThat(result).isPresent();

            @SuppressWarnings({"unchecked", "rawtypes"})
            org.mockito.ArgumentCaptor<List<ProductStatusEntity>> statusCaptor =
                    (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(List.class);
            verify(productJpaRepository).findStorefrontDetailWithOptionsById(
                    eq(rawId), statusCaptor.capture(), eq(CategoryStatusEntity.DELETED));

            List<ProductStatusEntity> excluded = statusCaptor.getValue();
            assertThat(excluded).containsExactlyInAnyOrder(ProductStatusEntity.DRAFT, ProductStatusEntity.DELETED);
            assertThat(excluded.stream()
                    .map(Enum::name)
                    .map(ProductStatus::fromString)
                    .allMatch(status -> !StorefrontProductVisibilityPolicy.isDetailViewable(status)))
                    .isTrue();
        }

        @Test
        @DisplayName("상품/옵션값 이미지를 매핑")
        void mapsProductAndOptionImages() {
            UUID rawId = UUID.randomUUID();
            ProductId productId = new ProductId(rawId);
            Fixture fixture = Fixture.forProduct(rawId);

            when(productJpaRepository.findStorefrontDetailWithOptionsById(eq(rawId), anyList(), eq(CategoryStatusEntity.DELETED)))
                    .thenReturn(Optional.of(fixture.product()));
            when(productJpaRepository.findStorefrontDetailWithVariantsById(eq(rawId), anyList(), eq(CategoryStatusEntity.DELETED)))
                    .thenReturn(Optional.of(fixture.product()));
            when(productDataAccessMapper.toDomainStatus(ProductStatusEntity.INACTIVE)).thenReturn(ProductStatus.INACTIVE);
            when(productDataAccessMapper.toDomainConditionType(ConditionTypeEntity.NEW)).thenReturn(ConditionType.NEW);
            when(productDataAccessMapper.toDomainOptionStatus(OptionStatusEntity.ACTIVE))
                    .thenReturn(com.project.young.productservice.domain.valueobject.OptionStatus.ACTIVE);
            when(productImageJpaRepository.findByProduct_IdAndStatusOrderBySortOrderAsc(rawId, OptionStatusEntity.ACTIVE))
                    .thenReturn(List.of(fixture.productImage()));
            when(productOptionValueImageJpaRepository.findByProductOptionValue_IdInAndStatusOrderBySortOrderAsc(any(), eq(OptionStatusEntity.ACTIVE)))
                    .thenReturn(List.of(fixture.povImage()));
            when(optionGroupJpaRepository.findAllByIdIn(any()))
                    .thenReturn(List.of(fixture.globalOptionGroup()));

            ReadProductDetailView view = publicProductReadRepository.findStorefrontProductDetailById(productId).orElseThrow();

            assertThat(view.images()).hasSize(1);
            assertThat(view.images().getFirst().publicUrl()).contains("products/p1.jpg");
            assertThat(view.optionGroups()).hasSize(1);
            assertThat(view.optionGroups().getFirst().groupKey()).isEqualTo("color");
            assertThat(view.optionGroups().getFirst().displayName()).isEqualTo("Color");
            assertThat(view.optionGroups().getFirst().optionValues()).hasSize(1);
            assertThat(view.optionGroups().getFirst().optionValues().getFirst().displayName()).isEqualTo("Red");
            assertThat(view.optionGroups().getFirst().optionValues().getFirst().images()).hasSize(1);
            assertThat(view.optionGroups().getFirst().optionValues().getFirst().images().getFirst().publicUrl())
                    .contains("products/pov/red.jpg");
        }

        @Test
        @DisplayName("null productId면 예외")
        void nullProductIdThrows() {
            assertThatThrownBy(() -> publicProductReadRepository.findStorefrontProductDetailById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("productId");
        }
    }

    private record Fixture(
            ProductEntity product,
            ProductImageEntity productImage,
            ProductOptionValueImageEntity povImage,
            OptionGroupEntity globalOptionGroup
    ) {
        static Fixture forProduct(UUID productId) {
            UUID globalOptionGroupId = UUID.randomUUID();
            UUID globalOptionValueId = UUID.randomUUID();
            ProductOptionValueEntity optionValue = ProductOptionValueEntity.builder()
                    .id(UUID.randomUUID())
                    .optionValueId(globalOptionValueId)
                    .priceDelta(BigDecimal.ZERO)
                    .isDefault(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            ProductOptionGroupEntity optionGroup = ProductOptionGroupEntity.builder()
                    .id(UUID.randomUUID())
                    .optionGroupId(globalOptionGroupId)
                    .stepOrder(1)
                    .isRequired(true)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            optionGroup.addOptionValue(optionValue);
            OptionValueEntity globalOptionValue = OptionValueEntity.builder()
                    .id(globalOptionValueId)
                    .value("RED")
                    .displayName("Red")
                    .sortOrder(0)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            OptionGroupEntity globalOptionGroup = OptionGroupEntity.builder()
                    .id(globalOptionGroupId)
                    .name("color")
                    .displayName("Color")
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            globalOptionGroup.addOptionValue(globalOptionValue);

            ProductEntity product = ProductEntity.builder()
                    .id(productId)
                    .name("preview")
                    .description("preview desc long enough")
                    .basePrice(new BigDecimal("15000"))
                    .status(ProductStatusEntity.INACTIVE)
                    .conditionType(ConditionTypeEntity.NEW)
                    .mainImageUrl("https://example.com/main.jpg")
                    .build();
            product.addOptionGroup(optionGroup);

            ProductImageEntity productImage = ProductImageEntity.builder()
                    .id(UUID.randomUUID())
                    .product(product)
                    .storageKey("products/p1.jpg")
                    .publicUrl("https://pub.example/products/p1.jpg")
                    .role(ProductImageRoleEntity.MAIN)
                    .sortOrder(0)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            ProductOptionValueImageEntity povImage = ProductOptionValueImageEntity.builder()
                    .id(UUID.randomUUID())
                    .productOptionValue(optionValue)
                    .storageKey("products/pov/red.jpg")
                    .publicUrl("https://pub.example/products/pov/red.jpg")
                    .role(ProductImageRoleEntity.MAIN)
                    .sortOrder(0)
                    .status(OptionStatusEntity.ACTIVE)
                    .build();
            return new Fixture(product, productImage, povImage, globalOptionGroup);
        }
    }
}

