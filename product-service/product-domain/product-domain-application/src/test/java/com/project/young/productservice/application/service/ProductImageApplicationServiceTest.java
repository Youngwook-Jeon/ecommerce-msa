package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.command.ReorderProductImagesCommand;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.application.port.output.ProductImagePersistencePort;
import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.repository.ProductRepository;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductImageRole;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ProductImageApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImagePersistencePort productImagePersistence;

    @Mock
    private ProductImageStoragePort productImageStorage;

    @Mock
    private VariantMainImageSyncPort variantMainImageSyncPort;

    @Mock
    private StorefrontProductCatalogInvalidationService storefrontProductCatalogInvalidationService;

    @InjectMocks
    private ProductImageApplicationService productImageApplicationService;

    @Test
    @DisplayName("commitUpload: 동일 objectKey가 이미 존재하면 idempotent하게 기존 레코드를 반환한다")
    void commitUpload_IdempotentWhenAlreadyCommitted() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = "products/" + productId + "/2026/05/a.jpg";
        String publicUrl = "https://pub.example/products/" + productId + "/2026/05/a.jpg";

        Product product = createProduct(productId, ProductStatus.DRAFT);
        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        ProductImagePersistencePort.ProductImageRow existing = new ProductImagePersistencePort.ProductImageRow(
                imageId, objectKey, publicUrl, ProductImageRole.MAIN, 0
        );

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        when(productImagePersistence.findByStorageKeyAndProductId(objectKey, productId))
                .thenReturn(Optional.of(existing));
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);

        CommitProductImageResult result = productImageApplicationService.commitUpload(productId, command);

        assertThat(result.id()).isEqualTo(imageId);
        assertThat(result.role()).isEqualTo("MAIN");
        assertThat(result.publicUrl()).isEqualTo(publicUrl);
        verify(productImagePersistence, never()).insert(any(), anyString(), anyString(), any(), anyInt(), anyString(), any());
        verify(productRepository).update(product);
    }

    @Test
    @DisplayName("commitUpload: 동일 objectKey가 다른 role로 이미 커밋되어 있으면 예외를 던진다")
    void commitUpload_ThrowsWhenExistingRoleDiffers() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = "products/" + productId + "/2026/05/a.jpg";
        String publicUrl = "https://pub.example/products/" + productId + "/2026/05/a.jpg";

        Product product = createProduct(productId, ProductStatus.DRAFT);
        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        ProductImagePersistencePort.ProductImageRow existing = new ProductImagePersistencePort.ProductImageRow(
                imageId, objectKey, publicUrl, ProductImageRole.GALLERY, 0
        );

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        when(productImagePersistence.findByStorageKeyAndProductId(objectKey, productId))
                .thenReturn(Optional.of(existing));
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);

        assertThatThrownBy(() -> productImageApplicationService.commitUpload(productId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("different role");
    }

    @Test
    @DisplayName("commitUpload: insert 경쟁 실패 시 재조회로 idempotent 처리한다")
    void commitUpload_RaceConditionFallsBackToExisting() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = "products/" + productId + "/2026/05/b.jpg";
        String publicUrl = "https://pub.example/products/" + productId + "/2026/05/b.jpg";

        Product product = createProduct(productId, ProductStatus.DRAFT);
        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(2048L)
                .role(ProductImageRole.GALLERY)
                .sortOrder(3)
                .build();

        ProductImagePersistencePort.ProductImageRow existing = new ProductImagePersistencePort.ProductImageRow(
                imageId, objectKey, publicUrl, ProductImageRole.GALLERY, 3
        );

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        doReturn(Optional.empty())
                .doReturn(Optional.of(existing))
                .when(productImagePersistence)
                .findByStorageKeyAndProductId(objectKey, productId);
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);
        when(productImagePersistence.insert(
                productId,
                objectKey,
                publicUrl,
                ProductImageRole.GALLERY,
                3,
                "image/jpeg",
                2048L
        )).thenThrow(new RuntimeException("duplicate key"));

        CommitProductImageResult result = productImageApplicationService.commitUpload(productId, command);

        assertThat(result.id()).isEqualTo(imageId);
        assertThat(result.role()).isEqualTo("GALLERY");
        verify(productRepository, never()).update(product);
    }

    @Test
    @DisplayName("reorderImages: 활성 이미지 전체 ID와 일치하면 순서를 재정렬한다")
    void reorderImages_ReindexesAllActiveImages() {
        UUID productId = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();
        Product product = createProduct(productId, ProductStatus.DRAFT);

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        doReturn(
                java.util.List.of(
                        new ProductImagePersistencePort.ProductImageRow(img1, "products/1.jpg", "https://pub/1.jpg", ProductImageRole.MAIN, 0),
                        new ProductImagePersistencePort.ProductImageRow(img2, "products/2.jpg", "https://pub/2.jpg", ProductImageRole.GALLERY, 1)
                ),
                java.util.List.of(
                        new ProductImagePersistencePort.ProductImageRow(img2, "products/2.jpg", "https://pub/2.jpg", ProductImageRole.GALLERY, 0),
                        new ProductImagePersistencePort.ProductImageRow(img1, "products/1.jpg", "https://pub/1.jpg", ProductImageRole.MAIN, 1)
                )
        ).when(productImagePersistence).findAllActiveByProductId(productId);
        when(productImagePersistence.updateSortOrder(img2, productId, 0)).thenReturn(1);
        when(productImagePersistence.updateSortOrder(img1, productId, 1)).thenReturn(1);

        ReorderProductImagesCommand command = ReorderProductImagesCommand.builder()
                .orderedImageIds(java.util.List.of(img2, img1))
                .build();

        ReorderProductImagesResult result = productImageApplicationService.reorderImages(productId, command);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.reorderedCount()).isEqualTo(2);
        verify(productImagePersistence).updateSortOrder(img2, productId, 0);
        verify(productImagePersistence).updateSortOrder(img1, productId, 1);
        verify(productImagePersistence).demoteActiveMainsToGallery(productId);
        verify(productImagePersistence).updateRole(img2, productId, ProductImageRole.MAIN);
        verify(productRepository).update(product);
    }

    @Test
    @DisplayName("reorderImages: 활성 이미지 전체를 포함하지 않으면 예외를 던진다")
    void reorderImages_ThrowsWhenIdsDoNotMatchActiveSet() {
        UUID productId = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();
        Product product = createProduct(productId, ProductStatus.DRAFT);

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        when(productImagePersistence.findAllActiveByProductId(productId)).thenReturn(java.util.List.of(
                new ProductImagePersistencePort.ProductImageRow(img1, "products/1.jpg", "https://pub/1.jpg", ProductImageRole.MAIN, 0),
                new ProductImagePersistencePort.ProductImageRow(img2, "products/2.jpg", "https://pub/2.jpg", ProductImageRole.GALLERY, 1)
        ));

        ReorderProductImagesCommand command = ReorderProductImagesCommand.builder()
                .orderedImageIds(java.util.List.of(img1))
                .build();

        assertThatThrownBy(() -> productImageApplicationService.reorderImages(productId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("must include all active");
    }

    @Test
    @DisplayName("deleteImage: 삭제 후 활성 이미지가 없으면 기본 메인 이미지로 복구한다")
    void deleteImage_WhenNoActiveImagesLeft_SetsDefaultMainImage() {
        UUID productId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        Product product = createProduct(productId, ProductStatus.DRAFT);

        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.of(product));
        when(productImagePersistence.findActiveByIdAndProduct(imageId, productId))
                .thenReturn(Optional.of(new ProductImagePersistencePort.ProductImageRow(
                        imageId, "products/1.jpg", "https://pub/1.jpg", ProductImageRole.MAIN, 0
                )));
        when(productImagePersistence.softDelete(imageId, productId)).thenReturn(1);
        when(productImagePersistence.findAllActiveByProductId(productId)).thenReturn(java.util.List.of());

        productImageApplicationService.deleteImage(productId, imageId);

        verify(productImagePersistence).softDelete(imageId, productId);
        verify(productRepository, times(1)).update(product);
        assertThat(product.getMainImageUrl()).isEqualTo(ProductImageApplicationService.DEFAULT_PRODUCT_IMAGE_URL);
    }

    private Product createProduct(UUID productId, ProductStatus status) {
        return Product.builder()
                .productId(new ProductId(productId))
                .categoryId(null)
                .name("테스트 상품")
                .description("테스트 상품 설명입니다. 20자 이상입니다.")
                .basePrice(new Money(new BigDecimal("10000")))
                .status(status)
                .conditionType(ConditionType.NEW)
                .brand("브랜드A")
                .mainImageUrl("https://example.com/main.jpg")
                .build();
    }
}
