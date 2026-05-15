package com.project.young.productservice.dataaccess.adapter;

import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.repository.ProductJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionGroupJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductOptionValueJpaRepository;
import com.project.young.productservice.dataaccess.repository.ProductVariantJpaRepository;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantMainImageSyncAdapterTest {

    private static final String PRODUCT_MAIN_URL = "https://example.com/product-main.jpg";
    private static final String POV_IMAGE_URL = "https://example.com/pov-main.jpg";
    private static final String DEFAULT_URL = "https://placehold.co/600x400?text=Product";

    @Mock
    private ProductVariantJpaRepository productVariantJpaRepository;

    @Mock
    private ProductJpaRepository productJpaRepository;

    @Mock
    private ProductOptionGroupJpaRepository productOptionGroupJpaRepository;

    @Mock
    private ProductOptionValueJpaRepository productOptionValueJpaRepository;

    @Mock
    private ProductOptionValueImagePersistencePort productOptionValueImagePersistence;

    @InjectMocks
    private VariantMainImageSyncAdapter adapter;

    @Test
    @DisplayName("syncByProductOptionValueId: visual 그룹이 아니면 variant를 갱신하지 않는다")
    void syncByProductOptionValueId_skipsWhenNotVisualGroup() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID visualPogId = UUID.randomUUID();
        UUID otherPogId = UUID.randomUUID();

        when(productOptionValueJpaRepository.findProductIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(productId));
        when(productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(otherPogId));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.of(ProductOptionGroupEntity.builder().id(visualPogId).build()));

        adapter.syncByProductOptionValueId(povId);

        verify(productVariantJpaRepository, never()).updateMainImageUrlForIds(anyList(), anyString());
    }

    @Test
    @DisplayName("syncByProductOptionValueId: visual POV이면 연결된 variant URL을 일괄 갱신한다")
    void syncByProductOptionValueId_updatesVariantsForVisualPov() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        when(productOptionValueJpaRepository.findProductIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(productId));
        when(productOptionValueJpaRepository.findProductOptionGroupIdByProductOptionValueId(povId))
                .thenReturn(Optional.of(pogId));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.of(ProductOptionGroupEntity.builder().id(pogId).build()));
        when(productVariantJpaRepository.findAllIdsByProductOptionValueId(povId))
                .thenReturn(List.of(variantId));
        when(productJpaRepository.findById(productId))
                .thenReturn(Optional.of(ProductEntity.builder().id(productId).mainImageUrl(PRODUCT_MAIN_URL).build()));
        when(productOptionValueImagePersistence.findAllActiveByProductOptionValueIds(List.of(povId)))
                .thenReturn(Map.of(
                        povId,
                        List.of(new ProductOptionValueImagePersistencePort.ProductImageRow(
                                UUID.randomUUID(),
                                "products/pov/a.jpg",
                                POV_IMAGE_URL,
                                ProductImageRole.MAIN,
                                0
                        ))
                ));

        adapter.syncByProductOptionValueId(povId);

        verify(productVariantJpaRepository).updateMainImageUrlForIds(List.of(variantId), POV_IMAGE_URL);
    }

    @Test
    @DisplayName("syncAllForProduct: visual 그룹이 없으면 상품 fallback URL로 일괄 갱신한다")
    void syncAllForProduct_usesProductFallbackWhenNoVisualGroup() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .id(variantId)
                .build();

        when(productJpaRepository.findById(productId))
                .thenReturn(Optional.of(ProductEntity.builder().id(productId).mainImageUrl(PRODUCT_MAIN_URL).build()));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.empty());
        when(productVariantJpaRepository.findAllByProductIdWithSelectedOptionValues(productId))
                .thenReturn(List.of(variant));

        adapter.syncAllForProduct(productId);

        verify(productVariantJpaRepository).updateMainImageUrlForIds(List.of(variantId), PRODUCT_MAIN_URL);
        verify(productOptionValueJpaRepository, never()).findPovIdAndGroupIdByProductId(productId);
    }

    @Test
    @DisplayName("syncAllForProduct: visual 그룹이 있으면 POV 이미지 URL별로 bulk 갱신한다")
    void syncAllForProduct_groupsUpdatesByResolvedUrl() {
        UUID productId = UUID.randomUUID();
        UUID visualPogId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        VariantOptionValueEntity selected = new VariantOptionValueEntity(null, null, povId);
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .id(variantId)
                .selectedOptionValues(Set.of(selected))
                .build();

        when(productJpaRepository.findById(productId))
                .thenReturn(Optional.of(ProductEntity.builder().id(productId).mainImageUrl(PRODUCT_MAIN_URL).build()));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.of(ProductOptionGroupEntity.builder().id(visualPogId).build()));
        when(productVariantJpaRepository.findAllByProductIdWithSelectedOptionValues(productId))
                .thenReturn(List.of(variant));
        when(productOptionValueJpaRepository.findPovIdAndGroupIdByProductId(productId))
                .thenReturn(List.<Object[]>of(new Object[]{povId, visualPogId}));
        when(productOptionValueImagePersistence.findAllActiveByProductOptionValueIds(List.of(povId)))
                .thenReturn(Map.of(
                        povId,
                        List.of(new ProductOptionValueImagePersistencePort.ProductImageRow(
                                UUID.randomUUID(),
                                "products/pov/a.jpg",
                                POV_IMAGE_URL,
                                ProductImageRole.MAIN,
                                0
                        ))
                ));

        adapter.syncAllForProduct(productId);

        verify(productVariantJpaRepository).updateMainImageUrlForIds(List.of(variantId), POV_IMAGE_URL);
    }

    @Test
    @DisplayName("syncForVariant: visual POV 이미지가 없으면 상품 fallback URL을 사용한다")
    void syncForVariant_usesProductFallbackWhenPovHasNoImages() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID visualPogId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();

        VariantOptionValueEntity selected = new VariantOptionValueEntity(null, null, povId);
        ProductEntity product = ProductEntity.builder().id(productId).mainImageUrl(PRODUCT_MAIN_URL).build();
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .id(variantId)
                .product(product)
                .selectedOptionValues(Set.of(selected))
                .build();

        when(productVariantJpaRepository.findByIdWithSelectedOptionValuesAndProduct(variantId))
                .thenReturn(Optional.of(variant));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.of(ProductOptionGroupEntity.builder().id(visualPogId).build()));
        when(productOptionValueJpaRepository.findPovIdAndGroupIdByPovIds(List.of(povId)))
                .thenReturn(List.<Object[]>of(new Object[]{povId, visualPogId}));
        when(productJpaRepository.findById(productId))
                .thenReturn(Optional.of(product));
        when(productOptionValueImagePersistence.findAllActiveByProductOptionValueIds(List.of(povId)))
                .thenReturn(Map.of());

        adapter.syncForVariant(variantId);

        verify(productVariantJpaRepository).updateMainImageUrl(variantId, PRODUCT_MAIN_URL);
    }

    @Test
    @DisplayName("syncForVariant: 상품 mainImageUrl이 비어 있으면 placeholder를 사용한다")
    void syncForVariant_usesPlaceholderWhenProductMainImageMissing() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        ProductEntity product = ProductEntity.builder().id(productId).mainImageUrl(" ").build();
        ProductVariantEntity variant = ProductVariantEntity.builder()
                .id(variantId)
                .product(product)
                .selectedOptionValues(Set.of())
                .build();

        when(productVariantJpaRepository.findByIdWithSelectedOptionValuesAndProduct(variantId))
                .thenReturn(Optional.of(variant));
        when(productOptionGroupJpaRepository.findActiveVisualGroupByProductId(productId, OptionStatusEntity.ACTIVE))
                .thenReturn(Optional.empty());
        when(productJpaRepository.findById(productId))
                .thenReturn(Optional.of(product));

        adapter.syncForVariant(variantId);

        verify(productVariantJpaRepository).updateMainImageUrl(variantId, DEFAULT_URL);
    }
}
