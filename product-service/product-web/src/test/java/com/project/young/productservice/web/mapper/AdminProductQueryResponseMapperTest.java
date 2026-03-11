package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadProductView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import com.project.young.productservice.web.converter.ConditionTypeWebConverter;
import com.project.young.productservice.web.converter.ProductStatusWebConverter;
import com.project.young.productservice.web.dto.AdminProductListItemResponse;
import com.project.young.productservice.web.dto.AdminProductPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminProductQueryResponseMapperTest {

    private AdminProductQueryResponseMapper adminProductQueryResponseMapper;

    @BeforeEach
    void setUp() {
        adminProductQueryResponseMapper = new AdminProductQueryResponseMapper(
                new ProductStatusWebConverter(),
                new ConditionTypeWebConverter()
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
}

