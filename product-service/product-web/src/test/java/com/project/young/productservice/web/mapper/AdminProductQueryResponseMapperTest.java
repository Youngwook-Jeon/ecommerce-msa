package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.AdminProductDetailResult;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AdminProductDetailResponse;
import com.project.young.productservice.web.dto.AdminProductListItemResponse;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminProductQueryResponseMapperTest {

    private AdminProductQueryResponseMapper adminProductQueryResponseMapper;

    @BeforeEach
    void setUp() {
        adminProductQueryResponseMapper = new AdminProductQueryResponseMapper(
                new ProductStatusWebConverter(),
                new ConditionTypeWebConverter(),
                new OptionStatusWebConverter()
        );
    }

    @Test
    @DisplayName("페이지 정보와 ReadProductView 리스트를 AdminProductPageResponse로 정상 매핑")
    void toAdminProductPageResponse_Success() {
        // Given
        UUID productId = UUID.randomUUID();
        ReadProductView view = ReadProductView.builder()
                .id(productId)
                .categoryId(1L)
                .name("와이드핏 데님")
                .description("와이드핏 데님 상세 설명입니다.")
                .brand("브랜드A")
                .mainImageUrl("https://example.com/image.jpg")
                .basePrice(new BigDecimal("99000"))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .build();

        // When
        AdminProductPageResponse response = adminProductQueryResponseMapper.toAdminProductPageResponse(
                List.of(view),
                0,
                20,
                1L,
                1
        );

        // Then
        assertThat(response).isNotNull();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1L);
        assertThat(response.totalPages()).isEqualTo(1);

        assertThat(response.content()).hasSize(1);
        AdminProductListItemResponse item = response.content().getFirst();
        assertThat(item.id()).isEqualTo(productId);
        assertThat(item.categoryId()).isEqualTo(1L);
        assertThat(item.name()).isEqualTo("와이드핏 데님");
        assertThat(item.brand()).isEqualTo("브랜드A");
        assertThat(item.mainImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(item.basePrice()).isEqualByComparingTo(new BigDecimal("99000"));
        assertThat(item.status()).isEqualTo("ACTIVE");
        assertThat(item.conditionType()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("AdminProductDetailResult를 옵션 그룹·변형 포함 AdminProductDetailResponse로 매핑")
    void toAdminProductDetailResponse_IncludesOptionGroupsAndVariants() {
        UUID productId = UUID.randomUUID();
        UUID pogId = UUID.randomUUID();
        UUID ogId = UUID.randomUUID();
        UUID povId = UUID.randomUUID();
        UUID ovId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant now = Instant.now();

        ReadProductOptionValueView valueView = ReadProductOptionValueView.builder()
                .productOptionValueId(povId)
                .optionValueId(ovId)
                .priceDelta(new BigDecimal("1000"))
                .isDefault(true)
                .status(OptionStatus.ACTIVE)
                .build();
        ReadProductOptionGroupView groupView = ReadProductOptionGroupView.builder()
                .productOptionGroupId(pogId)
                .optionGroupId(ogId)
                .stepOrder(1)
                .required(true)
                .status(OptionStatus.ACTIVE)
                .optionValues(List.of(valueView))
                .build();
        ReadProductVariantView variantView = ReadProductVariantView.builder()
                .productVariantId(variantId)
                .sku("SKU-1")
                .stockQuantity(10)
                .status(ProductStatus.ACTIVE)
                .calculatedPrice(new BigDecimal("100000"))
                .selectedProductOptionValueIds(List.of(povId))
                .build();

        AdminProductDetailResult result = AdminProductDetailResult.builder()
                .id(productId)
                .categoryId(1L)
                .name("상품")
                .description("설명 20자 이상 채워야 합니다.")
                .brand("브랜드")
                .mainImageUrl("https://example.com/x.jpg")
                .basePrice(new BigDecimal("99000"))
                .status(ProductStatus.ACTIVE)
                .conditionType(ConditionType.NEW)
                .createdAt(now)
                .updatedAt(now)
                .optionGroups(List.of(groupView))
                .variants(List.of(variantView))
                .build();

        AdminProductDetailResponse response = adminProductQueryResponseMapper.toAdminProductDetailResponse(result);

        assertThat(response.optionGroups()).hasSize(1);
        assertThat(response.optionGroups().getFirst().productOptionGroupId()).isEqualTo(pogId);
        assertThat(response.optionGroups().getFirst().optionValues()).hasSize(1);
        assertThat(response.optionGroups().getFirst().optionValues().getFirst().productOptionValueId()).isEqualTo(povId);
        assertThat(response.variants()).hasSize(1);
        assertThat(response.variants().getFirst().sku()).isEqualTo("SKU-1");
        assertThat(response.variants().getFirst().selectedProductOptionValueIds()).containsExactly(povId);
    }
}

