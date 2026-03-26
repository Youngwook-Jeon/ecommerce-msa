package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductOptionValueView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.ReadProductDetailResponse;
import com.project.young.productservice.web.dto.ReadProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductQueryResponseMapperTest {

    private ProductQueryResponseMapper productQueryResponseMapper;

    @BeforeEach
    void setUp() {
        productQueryResponseMapper = new ProductQueryResponseMapper(
                new ProductStatusWebConverter(),
                new ConditionTypeWebConverter()
        );
    }

    @Nested
    @DisplayName("toReadProductDetailResponse")
    class ToReadProductDetailResponseTests {

        @Test
        @DisplayName("readProductView가 null이면 NullPointerException")
        void nullView_ThrowsNullPointerException() {
            assertThatThrownBy(() -> productQueryResponseMapper.toReadProductDetailResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ReadProductDetailView is null");
        }

        @Test
        @DisplayName("정상 매핑")
        void success() {
            UUID id = UUID.randomUUID();
            UUID productOptionValueId = UUID.randomUUID();
            ReadProductDetailView view = ReadProductDetailView.builder()
                    .id(id)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .optionGroups(List.of(
                            ReadProductOptionGroupView.builder()
                                    .productOptionGroupId(UUID.randomUUID())
                                    .optionGroupId(UUID.randomUUID())
                                    .stepOrder(1)
                                    .required(true)
                                    .optionValues(List.of(
                                            ReadProductOptionValueView.builder()
                                                    .productOptionValueId(productOptionValueId)
                                                    .optionValueId(UUID.randomUUID())
                                                    .priceDelta(new BigDecimal("1000"))
                                                    .isDefault(true)
                                                    .isActive(true)
                                                    .build()
                                    ))
                                    .build()
                    ))
                    .variants(List.of(
                            ReadProductVariantView.builder()
                                    .productVariantId(UUID.randomUUID())
                                    .sku("SKU-001")
                                    .stockQuantity(3)
                                    .status(ProductStatus.ACTIVE)
                                    .calculatedPrice(new BigDecimal("100000"))
                                    .selectedProductOptionValueIds(List.of(productOptionValueId))
                                    .build()
                    ))
                    .build();

            ReadProductDetailResponse response = productQueryResponseMapper.toReadProductDetailResponse(view);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(id);
            assertThat(response.categoryId()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("와이드핏 데님");
            assertThat(response.description()).isEqualTo("와이드핏 데님 상세 설명입니다.");
            assertThat(response.brand()).isEqualTo("브랜드A");
            assertThat(response.mainImageUrl()).isEqualTo("https://example.com/image.jpg");
            assertThat(response.basePrice()).isEqualByComparingTo(new BigDecimal("99000"));
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.conditionType()).isEqualTo("NEW");
            assertThat(response.optionGroups()).hasSize(1);
            assertThat(response.variants()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("toReadProductListResponse")
    class ToReadProductListResponseTests {

        @Test
        @DisplayName("빈 리스트면 빈 products로 반환")
        void emptyList_ReturnsEmptyProducts() {
            ReadProductListResponse response = productQueryResponseMapper.toReadProductListResponse(List.of());

            assertThat(response).isNotNull();
            assertThat(response.products()).isEmpty();
        }

        @Test
        @DisplayName("여러 상품 정상 매핑")
        void multipleProducts_Success() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            ReadProductView view1 = ReadProductView.builder()
                    .id(id1)
                    .categoryId(1L)
                    .name("와이드핏 데님")
                    .description("와이드핏 데님 상세 설명입니다.")
                    .brand("브랜드A")
                    .mainImageUrl("https://example.com/image1.jpg")
                    .basePrice(new BigDecimal("99000"))
                    .status(ProductStatus.ACTIVE)
                    .conditionType(ConditionType.NEW)
                    .build();

            ReadProductView view2 = ReadProductView.builder()
                    .id(id2)
                    .categoryId(2L)
                    .name("스트레이트핏 데님")
                    .description("스트레이트핏 데님 상세 설명입니다.")
                    .brand("브랜드B")
                    .mainImageUrl("https://example.com/image2.jpg")
                    .basePrice(new BigDecimal("89000"))
                    .status(ProductStatus.INACTIVE)
                    .conditionType(ConditionType.USED)
                    .build();

            ReadProductListResponse response = productQueryResponseMapper.toReadProductListResponse(List.of(view1, view2));

            assertThat(response).isNotNull();
            assertThat(response.products()).hasSize(2);

            ReadProductDetailResponse p1 = response.products().get(0);
            ReadProductDetailResponse p2 = response.products().get(1);

            assertThat(p1.id()).isEqualTo(id1);
            assertThat(p1.name()).isEqualTo("와이드핏 데님");
            assertThat(p1.status()).isEqualTo("ACTIVE");

            assertThat(p2.id()).isEqualTo(id2);
            assertThat(p2.name()).isEqualTo("스트레이트핏 데님");
            assertThat(p2.status()).isEqualTo("INACTIVE");
        }
    }
}

