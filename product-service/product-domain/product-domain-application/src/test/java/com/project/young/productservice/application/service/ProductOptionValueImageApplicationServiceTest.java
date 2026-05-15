package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.command.CommitProductImageCommand;
import com.project.young.productservice.application.dto.command.PresignProductImageUploadCommand;
import com.project.young.productservice.application.dto.command.ReorderProductImagesCommand;
import com.project.young.productservice.application.dto.result.CommitProductImageResult;
import com.project.young.productservice.application.dto.result.PresignProductImageUploadResult;
import com.project.young.productservice.application.dto.result.ReorderProductImagesResult;
import com.project.young.productservice.application.port.output.ProductImageStoragePort;
import com.project.young.productservice.application.port.output.ProductOptionValueImagePersistencePort;
import com.project.young.productservice.application.port.output.VariantMainImageSyncPort;
import com.project.young.productservice.domain.entity.Product;
import com.project.young.productservice.domain.exception.ProductDomainException;
import com.project.young.productservice.domain.exception.ProductNotFoundException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionValueImageApplicationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionValueImagePersistencePort imagePersistence;

    @Mock
    private ProductImageStoragePort productImageStorage;

    @Mock
    private ProductOptionValueOwnershipValidator ownershipValidator;

    @Mock
    private VariantMainImageSyncPort variantMainImageSyncPort;

    @InjectMocks
    private ProductOptionValueImageApplicationService service;

    @Test
    @DisplayName("presignUpload: 소유권 검증 후 presigned URL을 반환한다")
    void presignUpload_returnsPresignedUrl() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        PresignProductImageUploadCommand command = PresignProductImageUploadCommand.builder()
                .fileName("color-red.jpg")
                .contentType("image/jpeg")
                .contentLength(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(productImageStorage.presignPut(anyString(), eq("image/jpeg"), eq(1024L), any(Duration.class)))
                .thenReturn(new ProductImageStoragePort.PresignedPutResult(
                        "https://upload.example/put",
                        "PUT",
                        Map.of("Content-Type", "image/jpeg"),
                        Instant.parse("2026-05-13T00:00:00Z")
                ));
        when(productImageStorage.publicUrlForKey(anyString())).thenReturn("https://pub.example/x.jpg");

        PresignProductImageUploadResult result = service.presignUpload(productId, povId, command);

        assertThat(result.uploadUrl()).isEqualTo("https://upload.example/put");
        assertThat(result.objectKey()).startsWith("products/" + productId + "/option-values/" + povId + "/");
        verify(ownershipValidator).requireOwnedByProduct(productId, povId);
        verify(productImageStorage).presignPut(anyString(), eq("image/jpeg"), eq(1024L), any(Duration.class));
        verify(imagePersistence, never()).findAllActiveByProductOptionValueId(any());
    }

    @Test
    @DisplayName("presignUpload: 삭제된 상품이면 예외를 던진다")
    void presignUpload_throwsWhenProductDeleted() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DELETED)));

        PresignProductImageUploadCommand command = PresignProductImageUploadCommand.builder()
                .fileName("a.jpg")
                .contentType("image/jpeg")
                .contentLength(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        assertThatThrownBy(() -> service.presignUpload(productId, povId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("deleted");

        verify(ownershipValidator, never()).requireOwnedByProduct(any(), any());
    }

    @Test
    @DisplayName("commitUpload: 동일 objectKey가 이미 존재하면 idempotent하게 기존 레코드를 반환한다")
    void commitUpload_idempotentWhenAlreadyCommitted() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = objectKey(productId, povId, "a.jpg");
        String publicUrl = "https://pub.example/" + objectKey;

        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();
        ProductOptionValueImagePersistencePort.ProductImageRow existing =
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        imageId, objectKey, publicUrl, ProductImageRole.MAIN, 0
                );

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(imagePersistence.findByStorageKeyAndProductOptionValueId(objectKey, povId))
                .thenReturn(Optional.of(existing));
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);

        CommitProductImageResult result = service.commitUpload(productId, povId, command);

        assertThat(result.id()).isEqualTo(imageId);
        assertThat(result.role()).isEqualTo("MAIN");
        verify(ownershipValidator).requireOwnedByProduct(productId, povId);
        verify(imagePersistence, never()).insert(
                any(), anyString(), anyString(), any(), anyInt(), anyString(), anyLong()
        );
        verify(variantMainImageSyncPort, never()).syncByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("commitUpload: 동일 objectKey가 다른 role로 이미 커밋되어 있으면 예외를 던진다")
    void commitUpload_throwsWhenExistingRoleDiffers() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = objectKey(productId, povId, "a.jpg");
        String publicUrl = "https://pub.example/" + objectKey;

        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();
        ProductOptionValueImagePersistencePort.ProductImageRow existing =
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        imageId, objectKey, publicUrl, ProductImageRole.GALLERY, 0
                );

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(imagePersistence.findByStorageKeyAndProductOptionValueId(objectKey, povId))
                .thenReturn(Optional.of(existing));
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);

        assertThatThrownBy(() -> service.commitUpload(productId, povId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("different role");
    }

    @Test
    @DisplayName("commitUpload: objectKey prefix가 POV와 맞지 않으면 예외를 던진다")
    void commitUpload_throwsWhenObjectKeyPrefixMismatch() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        String wrongKey = "products/" + productId + "/2026/05/wrong.jpg";

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));

        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(wrongKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        assertThatThrownBy(() -> service.commitUpload(productId, povId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("does not belong");

        verify(imagePersistence, never()).insert(
                any(), anyString(), anyString(), any(), anyInt(), anyString(), anyLong()
        );
    }

    @Test
    @DisplayName("commitUpload: 신규 insert 후 variant main image를 동기화한다")
    void commitUpload_insertsAndSyncsVariants() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = objectKey(productId, povId, "new.jpg");
        String publicUrl = "https://pub.example/" + objectKey;

        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .role(ProductImageRole.MAIN)
                .sortOrder(0)
                .build();

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(imagePersistence.findByStorageKeyAndProductOptionValueId(objectKey, povId))
                .thenReturn(Optional.empty());
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);
        when(imagePersistence.insert(
                povId, objectKey, publicUrl, ProductImageRole.MAIN, 0, "image/jpeg", 1024L
        )).thenReturn(imageId);

        CommitProductImageResult result = service.commitUpload(productId, povId, command);

        assertThat(result.id()).isEqualTo(imageId);
        verify(imagePersistence).demoteActiveMainsToGallery(povId);
        verify(variantMainImageSyncPort).syncByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("commitUpload: insert 경쟁 실패 시 재조회로 idempotent 처리한다")
    void commitUpload_raceConditionFallsBackToExisting() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        String objectKey = objectKey(productId, povId, "b.jpg");
        String publicUrl = "https://pub.example/" + objectKey;

        CommitProductImageCommand command = CommitProductImageCommand.builder()
                .objectKey(objectKey)
                .contentType("image/jpeg")
                .fileSize(2048L)
                .role(ProductImageRole.GALLERY)
                .sortOrder(3)
                .build();
        ProductOptionValueImagePersistencePort.ProductImageRow existing =
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        imageId, objectKey, publicUrl, ProductImageRole.GALLERY, 3
                );

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        doReturn(Optional.empty())
                .doReturn(Optional.of(existing))
                .when(imagePersistence)
                .findByStorageKeyAndProductOptionValueId(objectKey, povId);
        when(productImageStorage.publicUrlForKey(objectKey)).thenReturn(publicUrl);
        when(imagePersistence.findAllActiveByProductOptionValueId(povId)).thenReturn(List.of());
        when(imagePersistence.insert(
                povId, objectKey, publicUrl, ProductImageRole.GALLERY, 3, "image/jpeg", 2048L
        )).thenThrow(new RuntimeException("duplicate key"));

        CommitProductImageResult result = service.commitUpload(productId, povId, command);

        assertThat(result.id()).isEqualTo(imageId);
        assertThat(result.role()).isEqualTo("GALLERY");
        verify(variantMainImageSyncPort, never()).syncByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("reorderImages: 활성 이미지 전체 ID와 일치하면 순서를 재정렬하고 동기화한다")
    void reorderImages_reindexesAllActiveImages() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        doReturn(
                List.of(
                        new ProductOptionValueImagePersistencePort.ProductImageRow(
                                img1, objectKey(productId, povId, "1.jpg"), "https://pub/1.jpg", ProductImageRole.MAIN, 0),
                        new ProductOptionValueImagePersistencePort.ProductImageRow(
                                img2, objectKey(productId, povId, "2.jpg"), "https://pub/2.jpg", ProductImageRole.GALLERY, 1)
                ),
                List.of(
                        new ProductOptionValueImagePersistencePort.ProductImageRow(
                                img2, objectKey(productId, povId, "2.jpg"), "https://pub/2.jpg", ProductImageRole.GALLERY, 0),
                        new ProductOptionValueImagePersistencePort.ProductImageRow(
                                img1, objectKey(productId, povId, "1.jpg"), "https://pub/1.jpg", ProductImageRole.MAIN, 1)
                )
        ).when(imagePersistence).findAllActiveByProductOptionValueId(povId);
        when(imagePersistence.updateSortOrder(img2, povId, 0)).thenReturn(1);
        when(imagePersistence.updateSortOrder(img1, povId, 1)).thenReturn(1);

        ReorderProductImagesCommand command = ReorderProductImagesCommand.builder()
                .orderedImageIds(List.of(img2, img1))
                .build();

        ReorderProductImagesResult result = service.reorderImages(productId, povId, command);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.reorderedCount()).isEqualTo(2);
        verify(ownershipValidator).requireOwnedByProduct(productId, povId);
        verify(imagePersistence).updateSortOrder(img2, povId, 0);
        verify(imagePersistence).updateSortOrder(img1, povId, 1);
        verify(imagePersistence).demoteActiveMainsToGallery(povId);
        verify(imagePersistence).updateRole(img2, povId, ProductImageRole.MAIN);
        verify(variantMainImageSyncPort).syncByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("reorderImages: 활성 이미지 전체를 포함하지 않으면 예외를 던진다")
    void reorderImages_throwsWhenIdsDoNotMatchActiveSet() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(imagePersistence.findAllActiveByProductOptionValueId(povId)).thenReturn(List.of(
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        img1, objectKey(productId, povId, "1.jpg"), "https://pub/1.jpg", ProductImageRole.MAIN, 0),
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        img2, objectKey(productId, povId, "2.jpg"), "https://pub/2.jpg", ProductImageRole.GALLERY, 1)
        ));

        ReorderProductImagesCommand command = ReorderProductImagesCommand.builder()
                .orderedImageIds(List.of(img1))
                .build();

        assertThatThrownBy(() -> service.reorderImages(productId, povId, command))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("must include all active");

        verify(variantMainImageSyncPort, never()).syncByProductOptionValueId(any());
    }

    @Test
    @DisplayName("deleteImage: 삭제 후 첫 활성 이미지를 MAIN으로 승격하고 variant를 동기화한다")
    void deleteImage_promotesFirstActiveAndSyncsVariants() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        UUID remainingId = UUID.randomUUID();

        when(productRepository.findById(any(ProductId.class)))
                .thenReturn(Optional.of(reconstituteProduct(productId, ProductStatus.DRAFT)));
        when(imagePersistence.findActiveByIdAndProductOptionValue(imageId, povId))
                .thenReturn(Optional.of(new ProductOptionValueImagePersistencePort.ProductImageRow(
                        imageId, objectKey(productId, povId, "del.jpg"), "https://pub/del.jpg", ProductImageRole.MAIN, 0
                )));
        when(imagePersistence.softDelete(imageId, povId)).thenReturn(1);
        when(imagePersistence.findAllActiveByProductOptionValueId(povId)).thenReturn(List.of(
                new ProductOptionValueImagePersistencePort.ProductImageRow(
                        remainingId,
                        objectKey(productId, povId, "remain.jpg"),
                        "https://pub/remain.jpg",
                        ProductImageRole.GALLERY,
                        0
                )
        ));

        service.deleteImage(productId, povId, imageId);

        verify(imagePersistence).softDelete(imageId, povId);
        verify(imagePersistence).demoteActiveMainsToGallery(povId);
        verify(imagePersistence).updateRole(remainingId, povId, ProductImageRole.MAIN);
        verify(variantMainImageSyncPort).syncByProductOptionValueId(povId);
    }

    @Test
    @DisplayName("deleteImage: 상품이 없으면 ProductNotFoundException을 던진다")
    void deleteImage_throwsWhenProductNotFound() {
        UUID productId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(productRepository.findById(any(ProductId.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteImage(productId, povId, imageId))
                .isInstanceOf(ProductNotFoundException.class);

        verify(imagePersistence, never()).softDelete(any(), any());
    }

    private static String objectKey(UUID productId, UUID povId, String fileName) {
        return "products/" + productId + "/option-values/" + povId + "/2026/05/" + fileName;
    }

    private static Product reconstituteProduct(UUID productId, ProductStatus status) {
        return Product.reconstitute(
                new ProductId(productId),
                null,
                "테스트 상품",
                "테스트 상품 설명입니다. 20자 이상입니다.",
                new Money(new BigDecimal("10000")),
                status,
                ConditionType.NEW,
                "브랜드A",
                "https://example.com/main.jpg",
                List.of(),
                List.of()
        );
    }
}
