package com.project.young.productservice.dataaccess.repository;

import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueEntity;
import com.project.young.productservice.dataaccess.entity.ProductOptionValueImageEntity;
import com.project.young.productservice.dataaccess.entity.ProductVariantEntity;
import com.project.young.productservice.dataaccess.entity.VariantOptionValueEntity;
import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.dataaccess.enums.ProductImageRoleEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.UUID;

final class JpaRepositoryTestFixtures {

    private JpaRepositoryTestFixtures() {
    }

    static void truncateCompositionTables(TestEntityManager testEntityManager) {
        testEntityManager.getEntityManager()
                .createNativeQuery("""
                        TRUNCATE TABLE
                            product_option_value_images,
                            variant_option_values,
                            product_variants,
                            product_option_values,
                            product_option_groups,
                            products,
                            option_values,
                            option_groups
                        RESTART IDENTITY CASCADE
                        """)
                .executeUpdate();
        testEntityManager.flush();
        testEntityManager.clear();
    }

    static CompositionGraph persistColorSizeProduct(TestEntityManager testEntityManager) {
        OptionGroupEntity colorGlobal = testEntityManager.persistAndFlush(OptionGroupEntity.builder()
                .name("color-" + UUID.randomUUID())
                .displayName("색상")
                .status(OptionStatusEntity.ACTIVE)
                .build());
        OptionValueEntity redGlobal = testEntityManager.persistAndFlush(OptionValueEntity.builder()
                .optionGroup(colorGlobal)
                .value("red")
                .displayName("빨강")
                .sortOrder(1)
                .status(OptionStatusEntity.ACTIVE)
                .build());
        OptionValueEntity blueGlobal = testEntityManager.persistAndFlush(OptionValueEntity.builder()
                .optionGroup(colorGlobal)
                .value("blue")
                .displayName("파랑")
                .sortOrder(2)
                .status(OptionStatusEntity.ACTIVE)
                .build());

        OptionGroupEntity sizeGlobal = testEntityManager.persistAndFlush(OptionGroupEntity.builder()
                .name("size-" + UUID.randomUUID())
                .displayName("사이즈")
                .status(OptionStatusEntity.ACTIVE)
                .build());
        OptionValueEntity largeGlobal = testEntityManager.persistAndFlush(OptionValueEntity.builder()
                .optionGroup(sizeGlobal)
                .value("L")
                .displayName("라지")
                .sortOrder(1)
                .status(OptionStatusEntity.ACTIVE)
                .build());

        UUID productId = UUID.randomUUID();
        UUID colorGroupId = UUID.randomUUID();
        UUID sizeGroupId = UUID.randomUUID();
        UUID redPovId = UUID.randomUUID();
        UUID bluePovId = UUID.randomUUID();
        UUID largePovId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        ProductEntity product = ProductEntity.builder()
                .id(productId)
                .name("테스트 상품")
                .description("테스트 상품 설명입니다. 20자 이상입니다.")
                .basePrice(new BigDecimal("10000"))
                .status(ProductStatusEntity.ACTIVE)
                .conditionType(ConditionTypeEntity.NEW)
                .brand("브랜드A")
                .mainImageUrl("https://example.com/product-main.jpg")
                .build();

        ProductOptionGroupEntity colorGroup = ProductOptionGroupEntity.builder()
                .id(colorGroupId)
                .optionGroupId(colorGlobal.getId())
                .stepOrder(1.0d)
                .isRequired(true)
                .drivesVariantImages(false)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueEntity redPov = ProductOptionValueEntity.builder()
                .id(redPovId)
                .optionValueId(redGlobal.getId())
                .priceDelta(BigDecimal.ZERO)
                .isDefault(true)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueEntity bluePov = ProductOptionValueEntity.builder()
                .id(bluePovId)
                .optionValueId(blueGlobal.getId())
                .priceDelta(new BigDecimal("100"))
                .isDefault(false)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        colorGroup.addOptionValue(redPov);
        colorGroup.addOptionValue(bluePov);
        product.addOptionGroup(colorGroup);

        ProductOptionGroupEntity sizeGroup = ProductOptionGroupEntity.builder()
                .id(sizeGroupId)
                .optionGroupId(sizeGlobal.getId())
                .stepOrder(2.0d)
                .isRequired(true)
                .drivesVariantImages(false)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        ProductOptionValueEntity largePov = ProductOptionValueEntity.builder()
                .id(largePovId)
                .optionValueId(largeGlobal.getId())
                .priceDelta(BigDecimal.ZERO)
                .isDefault(true)
                .status(OptionStatusEntity.ACTIVE)
                .build();
        sizeGroup.addOptionValue(largePov);
        product.addOptionGroup(sizeGroup);

        ProductVariantEntity variant = ProductVariantEntity.builder()
                .id(variantId)
                .sku("SKU-TEST-001")
                .stockQuantity(10)
                .status(ProductStatusEntity.ACTIVE)
                .calculatedPrice(new BigDecimal("10000"))
                .mainImageUrl("https://example.com/old-variant.jpg")
                .build();
        variant.addSelectedOptionValue(VariantOptionValueEntity.builder()
                .productOptionValueId(redPovId)
                .build());
        variant.addSelectedOptionValue(VariantOptionValueEntity.builder()
                .productOptionValueId(largePovId)
                .build());
        product.addVariant(variant);

        testEntityManager.persistAndFlush(product);
        testEntityManager.clear();

        return new CompositionGraph(
                productId,
                colorGroupId,
                sizeGroupId,
                redPovId,
                bluePovId,
                largePovId,
                variantId
        );
    }

    static ProductOptionValueImageEntity createPovImage(
            ProductOptionValueEntity pov,
            String storageKey,
            ProductImageRoleEntity role,
            int sortOrder,
            OptionStatusEntity status
    ) {
        return ProductOptionValueImageEntity.builder()
                .productOptionValue(pov)
                .storageKey(storageKey)
                .publicUrl("https://pub.example/" + storageKey)
                .role(role)
                .sortOrder(sortOrder)
                .contentType("image/jpeg")
                .fileSize(1024L)
                .status(status)
                .build();
    }

    record CompositionGraph(
            UUID productId,
            UUID colorGroupId,
            UUID sizeGroupId,
            UUID redPovId,
            UUID bluePovId,
            UUID largePovId,
            UUID variantId
    ) {
    }
}
