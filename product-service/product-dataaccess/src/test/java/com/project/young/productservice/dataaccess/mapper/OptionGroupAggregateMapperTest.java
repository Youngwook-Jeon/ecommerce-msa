package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.dataaccess.entity.OptionGroupEntity;
import com.project.young.productservice.dataaccess.entity.OptionValueEntity;
import com.project.young.productservice.dataaccess.enums.OptionStatusEntity;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OptionGroupAggregateMapperTest {

    private final OptionGroupAggregateMapper mapper = new OptionGroupAggregateMapper();

    @Test
    @DisplayName("toOptionGroup: 엔티티를 도메인으로 올바르게 매핑한다")
    void toOptionGroup_Success() {
        UUID groupId = UUID.randomUUID();
        UUID valueId = UUID.randomUUID();

        OptionGroupEntity entity = OptionGroupEntity.builder()
                .id(groupId)
                .name("COLOR")
                .displayName("색상")
                .status(OptionStatusEntity.ACTIVE)
                .optionValues(new ArrayList<>())
                .build();

        OptionValueEntity valueEntity = OptionValueEntity.builder()
                .id(valueId)
                .value("RED")
                .displayName("빨강")
                .sortOrder(1)
                .status(OptionStatusEntity.INACTIVE)
                .build();
        entity.addOptionValue(valueEntity);

        OptionGroup domain = mapper.toOptionGroup(entity);

        assertThat(domain.getId()).isEqualTo(new OptionGroupId(groupId));
        assertThat(domain.getName()).isEqualTo("COLOR");
        assertThat(domain.getDisplayName()).isEqualTo("색상");
        assertThat(domain.getStatus()).isEqualTo(OptionStatus.ACTIVE);
        assertThat(domain.getOptionValues()).hasSize(1);
        assertThat(domain.getOptionValues().get(0).getId()).isEqualTo(new OptionValueId(valueId));
        assertThat(domain.getOptionValues().get(0).getValue()).isEqualTo("RED");
        assertThat(domain.getOptionValues().get(0).getDisplayName()).isEqualTo("빨강");
        assertThat(domain.getOptionValues().get(0).getSortOrder()).isEqualTo(1);
        assertThat(domain.getOptionValues().get(0).getStatus()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("toOptionGroup: 엔티티가 null이면 NullPointerException")
    void toOptionGroup_NullEntity_ThrowsNpe() {
        assertThatThrownBy(() -> mapper.toOptionGroup(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entity must not be null");
    }

    @Test
    @DisplayName("toOptionGroup: 엔티티 ID가 null이면 NullPointerException")
    void toOptionGroup_NullEntityId_ThrowsNpe() {
        OptionGroupEntity entity = OptionGroupEntity.builder()
                .id(null)
                .name("COLOR")
                .displayName("색상")
                .status(OptionStatusEntity.ACTIVE)
                .optionValues(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> mapper.toOptionGroup(entity))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entity ID must not be null");
    }
}
