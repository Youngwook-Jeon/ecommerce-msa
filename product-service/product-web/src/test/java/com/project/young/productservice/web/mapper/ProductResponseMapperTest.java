package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.result.AddProductOptionGroupResult;
import com.project.young.productservice.application.dto.result.AddProductOptionValueToGroupResult;
import com.project.young.productservice.application.dto.result.AddProductVariantResult;
import com.project.young.productservice.application.dto.result.CreateProductResult;
import com.project.young.productservice.application.dto.result.DeleteProductResult;
import com.project.young.productservice.application.dto.result.UpdateProductResult;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AddProductOptionGroupResponse;
import com.project.young.productservice.web.dto.AddProductOptionValueToGroupResponse;
import com.project.young.productservice.web.dto.AddProductVariantResponse;
import com.project.young.productservice.web.dto.CreateProductResponse;
import com.project.young.productservice.web.dto.DeleteProductResponse;
import com.project.young.productservice.web.dto.UpdateProductResponse;
import com.project.young.productservice.web.message.ProductResponseMessageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseMapperTest {

    private ProductResponseMapper productResponseMapper;

    @BeforeEach
    void setUp() {
        productResponseMapper = new ProductResponseMapper(
                new ProductResponseMessageFactory(),
                new ProductStatusWebConverter(),
                new ConditionTypeWebConverter()
        );
    }

    @Nested
    @DisplayName("toCreateProductResponse")
    class ToCreateProductResponseTests {

        @Test
        @DisplayName("정상 매핑")
        void success() {
            UUID id = UUID.randomUUID();
            CreateProductResult result = new CreateProductResult(id, "와이드핏 데님");

            CreateProductResponse response = productResponseMapper.toCreateProductResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(id);
            assertThat(response.name()).isEqualTo("와이드핏 데님");
            assertThat(response.message()).containsIgnoringCase("created");
        }
    }

    @Nested
    @DisplayName("toUpdateProductResponse")
    class ToUpdateProductResponseTests {

        @Test
        @DisplayName("정상 매핑")
        void success() {
            UUID id = UUID.randomUUID();
            UpdateProductResult result = new UpdateProductResult(
                    id,
                    "와이드핏 데님",
                    1L,
                    "와이드핏 데님 상세 설명입니다.",
                    "브랜드A",
                    "https://example.com/image.jpg",
                    new BigDecimal("99000"),
                    ProductStatus.ACTIVE,
                    ConditionType.NEW
            );

            UpdateProductResponse response = productResponseMapper.toUpdateProductResponse(result);

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
            assertThat(response.message()).containsIgnoringCase("updated");
        }
    }

    @Nested
    @DisplayName("toUpdateProductStatusResponse")
    class ToUpdateProductStatusResponseTests {

        @Test
        @DisplayName("정상 매핑 — 메시지는 status 업데이트용 문구")
        void success() {
            UUID id = UUID.randomUUID();
            UpdateProductResult result = new UpdateProductResult(
                    id,
                    "와이드핏 데님",
                    1L,
                    "와이드핏 데님 상세 설명입니다.",
                    "브랜드A",
                    "https://example.com/image.jpg",
                    new BigDecimal("99000"),
                    ProductStatus.INACTIVE,
                    ConditionType.NEW
            );

            UpdateProductResponse response = productResponseMapper.toUpdateProductStatusResponse(result);

            assertThat(response.status()).isEqualTo("INACTIVE");
            assertThat(response.message()).containsIgnoringCase("status");
            assertThat(response.message()).containsIgnoringCase("updated");
        }
    }

    @Nested
    @DisplayName("toDeleteProductResponse")
    class ToDeleteProductResponseTests {

        @Test
        @DisplayName("정상 매핑")
        void success() {
            UUID id = UUID.randomUUID();
            DeleteProductResult result = new DeleteProductResult(id, "와이드핏 데님");

            DeleteProductResponse response = productResponseMapper.toDeleteProductResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(id);
            assertThat(response.name()).isEqualTo("와이드핏 데님");
            assertThat(response.message()).containsIgnoringCase("deleted");
        }
    }

    @Nested
    @DisplayName("toAddProductOptionGroupResponse / toAddProductOptionValueToGroupResponse / toAddProductVariantResponse")
    class CompositionMappingTests {

        @Test
        @DisplayName("구성 API 응답 매핑")
        void compositionResponses() {
            UUID pid = UUID.randomUUID();
            UUID pog = UUID.randomUUID();
            UUID globalOg = UUID.randomUUID();
            UUID pov = UUID.randomUUID();
            UUID globalVal = UUID.randomUUID();
            UUID varId = UUID.randomUUID();

            AddProductOptionGroupResponse g = productResponseMapper.toAddProductOptionGroupResponse(
                    AddProductOptionGroupResult.builder()
                            .productId(pid)
                            .productOptionGroupId(pog)
                            .optionGroupId(globalOg)
                            .stepOrder(2)
                            .required(false)
                            .optionValueCount(0)
                            .build());
            assertThat(g.productOptionGroupId()).isEqualTo(pog);
            assertThat(g.message()).containsIgnoringCase("option group");

            AddProductOptionValueToGroupResponse v = productResponseMapper.toAddProductOptionValueToGroupResponse(
                    AddProductOptionValueToGroupResult.builder()
                            .productId(pid)
                            .productOptionGroupId(pog)
                            .productOptionValueId(pov)
                            .optionValueId(globalVal)
                            .priceDelta(new BigDecimal("100"))
                            .build());
            assertThat(v.productOptionValueId()).isEqualTo(pov);
            assertThat(v.message()).containsIgnoringCase("option value");

            AddProductVariantResponse r = productResponseMapper.toAddProductVariantResponse(
                    AddProductVariantResult.builder()
                            .productId(pid)
                            .productVariantId(varId)
                            .sku("SKU")
                            .stockQuantity(3)
                            .status(ProductStatus.ACTIVE)
                            .calculatedPrice(new BigDecimal("999"))
                            .build());
            assertThat(r.sku()).isEqualTo("SKU");
            assertThat(r.status()).isEqualTo("ACTIVE");
            assertThat(r.message()).containsIgnoringCase("variant");
        }
    }
}

