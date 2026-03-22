package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.port.output.view.ReadOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadOptionValueView;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import com.project.young.productservice.web.converter.OptionStatusWebConverter;
import com.project.young.productservice.web.dto.ReadOptionGroupListQueryResponse;
import com.project.young.productservice.web.dto.ReadOptionGroupQueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionGroupQueryResponseMapperTest {

    private OptionGroupQueryResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OptionGroupQueryResponseMapper(new OptionStatusWebConverter());
    }

    @Nested
    @DisplayName("toReadOptionGroupQueryResponse")
    class ToReadOptionGroupQueryResponseTests {

        @Test
        @DisplayName("view가 null이면 NullPointerException")
        void nullView_Throws() {
            assertThatThrownBy(() -> mapper.toReadOptionGroupQueryResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ReadOptionGroupView is null");
        }

        @Test
        @DisplayName("옵션 값 포함 정상 매핑")
        void success_WithValues() {
            UUID groupId = UUID.randomUUID();
            UUID valueId = UUID.randomUUID();

            ReadOptionGroupView view = ReadOptionGroupView.builder()
                    .id(groupId)
                    .name("COLOR")
                    .displayName("색상")
                    .status(OptionStatus.ACTIVE)
                    .optionValues(List.of(
                            ReadOptionValueView.builder()
                                    .id(valueId)
                                    .value("RED")
                                    .displayName("빨강")
                                    .sortOrder(1)
                                    .status(OptionStatus.ACTIVE)
                                    .build()
                    ))
                    .build();

            ReadOptionGroupQueryResponse response = mapper.toReadOptionGroupQueryResponse(view);

            assertThat(response.id()).isEqualTo(groupId);
            assertThat(response.name()).isEqualTo("COLOR");
            assertThat(response.displayName()).isEqualTo("색상");
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.optionValues()).hasSize(1);
            assertThat(response.optionValues().get(0).id()).isEqualTo(valueId);
            assertThat(response.optionValues().get(0).value()).isEqualTo("RED");
            assertThat(response.optionValues().get(0).sortOrder()).isEqualTo(1);
            assertThat(response.optionValues().get(0).status()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("toReadOptionGroupListQueryResponse")
    class ToReadOptionGroupListQueryResponseTests {

        @Test
        @DisplayName("빈 리스트면 빈 optionGroups")
        void emptyList_ReturnsEmpty() {
            ReadOptionGroupListQueryResponse response = mapper.toReadOptionGroupListQueryResponse(List.of());

            assertThat(response.optionGroups()).isEmpty();
        }

        @Test
        @DisplayName("여러 그룹 매핑")
        void multipleGroups_Success() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            ReadOptionGroupView v1 = ReadOptionGroupView.builder()
                    .id(id1)
                    .name("A")
                    .displayName("에이")
                    .status(OptionStatus.ACTIVE)
                    .optionValues(List.of())
                    .build();
            ReadOptionGroupView v2 = ReadOptionGroupView.builder()
                    .id(id2)
                    .name("B")
                    .displayName("비")
                    .status(OptionStatus.ACTIVE)
                    .optionValues(List.of())
                    .build();

            ReadOptionGroupListQueryResponse response =
                    mapper.toReadOptionGroupListQueryResponse(List.of(v1, v2));

            assertThat(response.optionGroups()).hasSize(2);
            assertThat(response.optionGroups().get(0).name()).isEqualTo("A");
            assertThat(response.optionGroups().get(1).name()).isEqualTo("B");
        }
    }
}
