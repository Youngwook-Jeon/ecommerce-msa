package com.project.young.productservice.dataaccess.cache;

import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadProductDetailViewJsonMapperTest {

    private ReadProductDetailViewJsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        jsonMapper = new ReadProductDetailViewJsonMapper();
    }

    @Test
    @DisplayName("toJson/fromJson: ReadProductDetailView를 round-trip 직렬화한다")
    void roundTrip_preservesProductDetailView() {
        ReadProductDetailView view = sampleView();

        String json = jsonMapper.toJson(view);
        ReadProductDetailView restored = jsonMapper.fromJson(json);

        assertThat(restored).isEqualTo(view);
        assertThat(json).contains("\"name\":\"Preview Product\"");
        assertThat(json).contains("\"status\":\"INACTIVE\"");
    }

    @Test
    @DisplayName("fromJson: 잘못된 JSON이면 IllegalStateException")
    void fromJson_whenInvalidJson_throwsIllegalStateException() {
        assertThatThrownBy(() -> jsonMapper.fromJson("{invalid"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to deserialize ReadProductDetailView");
    }

    private static ReadProductDetailView sampleView() {
        UUID productId = UUID.fromString("019ebfe7-e8cb-7f79-9d0b-c51e5a60bf06");
        return ReadProductDetailView.builder()
                .id(productId)
                .categoryId(4L)
                .name("Preview Product")
                .description("desc")
                .brand("Brand")
                .mainImageUrl("https://example.com/a.jpg")
                .basePrice(new BigDecimal("10000"))
                .status(ProductStatus.INACTIVE)
                .conditionType(ConditionType.NEW)
                .optionGroups(List.of())
                .variants(List.of())
                .build();
    }
}
