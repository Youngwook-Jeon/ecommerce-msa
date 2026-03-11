package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.CreateProductResult;
import com.project.young.productservice.application.dto.DeleteProductResult;
import com.project.young.productservice.application.dto.UpdateProductResult;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
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
}

