package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.application.dto.command.AddOptionValueCommand;
import com.project.young.productservice.application.dto.command.CreateOptionGroupCommand;
import com.project.young.productservice.application.dto.result.*;
import com.project.young.productservice.domain.entity.OptionGroup;
import com.project.young.productservice.domain.entity.OptionValue;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OptionGroupDataMapperTest {

    private OptionGroupDataMapper optionGroupDataMapper;

    @BeforeEach
    void setUp() {
        optionGroupDataMapper = new OptionGroupDataMapper();
    }

    @Test
    @DisplayName("toCreateOptionGroupResult: OptionGroup가 null이면 NullPointerException")
    void toCreateOptionGroupResult_NullOptionGroup_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toCreateOptionGroupResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionGroup cannot be null");
    }

    @Test
    @DisplayName("toCreateOptionGroupResult: OptionGroup ID가 null이면 NullPointerException")
    void toCreateOptionGroupResult_NullOptionGroupId_ThrowsNpe() {
        OptionGroup optionGroupWithoutId = OptionGroup.builder()
                .name("COLOR")
                .displayName("색상")
                .build();

        assertThatThrownBy(() -> optionGroupDataMapper.toCreateOptionGroupResult(optionGroupWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionGroup ID cannot be null");
    }

    @Test
    @DisplayName("toCreateOptionGroupResult: 정상 매핑")
    void toCreateOptionGroupResult_Success() {
        UUID id = UUID.randomUUID();
        OptionGroup optionGroup = OptionGroup.reconstitute(
                new OptionGroupId(id),
                "COLOR",
                "색상",
                OptionStatus.ACTIVE,
                List.of()
        );

        CreateOptionGroupResult result = optionGroupDataMapper.toCreateOptionGroupResult(optionGroup);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("COLOR");
    }

    @Test
    @DisplayName("toOptionGroup: command가 null이면 NullPointerException")
    void toOptionGroup_NullCommand_ThrowsNpe() {
        OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());

        assertThatThrownBy(() -> optionGroupDataMapper.toOptionGroup(null, groupId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("CreateOptionGroupCommand cannot be null");
    }

    @Test
    @DisplayName("toOptionGroup: command와 ID로 OptionGroup 매핑")
    void toOptionGroup_Success() {
        OptionGroupId groupId = new OptionGroupId(UUID.randomUUID());
        CreateOptionGroupCommand command = CreateOptionGroupCommand.builder()
                .name("SIZE")
                .displayName("사이즈")
                .build();

        OptionGroup optionGroup = optionGroupDataMapper.toOptionGroup(command, groupId);

        assertThat(optionGroup).isNotNull();
        assertThat(optionGroup.getId()).isEqualTo(groupId);
        assertThat(optionGroup.getName()).isEqualTo("SIZE");
        assertThat(optionGroup.getDisplayName()).isEqualTo("사이즈");
        assertThat(optionGroup.getStatus()).isEqualTo(OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("toUpdateOptionGroupResult: OptionGroup가 null이면 NullPointerException")
    void toUpdateOptionGroupResult_NullOptionGroup_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toUpdateOptionGroupResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionGroup cannot be null");
    }

    @Test
    @DisplayName("toUpdateOptionGroupResult: 정상 매핑")
    void toUpdateOptionGroupResult_Success() {
        UUID id = UUID.randomUUID();
        OptionGroup optionGroup = OptionGroup.reconstitute(
                new OptionGroupId(id),
                "STORAGE",
                "저장용량",
                OptionStatus.INACTIVE,
                List.of()
        );

        UpdateOptionGroupResult result = optionGroupDataMapper.toUpdateOptionGroupResult(optionGroup);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("STORAGE");
        assertThat(result.displayName()).isEqualTo("저장용량");
        assertThat(result.status()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("toDeleteOptionGroupResult: OptionGroup가 null이면 NullPointerException")
    void toDeleteOptionGroupResult_NullOptionGroup_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toDeleteOptionGroupResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionGroup cannot be null");
    }

    @Test
    @DisplayName("toDeleteOptionGroupResult: 정상 매핑")
    void toDeleteOptionGroupResult_Success() {
        UUID id = UUID.randomUUID();
        OptionGroup optionGroup = OptionGroup.reconstitute(
                new OptionGroupId(id),
                "삭제대상",
                "표시명",
                OptionStatus.DELETED,
                List.of()
        );

        DeleteOptionGroupResult result = optionGroupDataMapper.toDeleteOptionGroupResult(optionGroup);

        assertThat(result.id()).isEqualTo(id);
        assertThat(result.name()).isEqualTo("삭제대상");
    }

    @Test
    @DisplayName("toOptionValue: command가 null이면 NullPointerException")
    void toOptionValue_NullCommand_ThrowsNpe() {
        OptionValueId valueId = new OptionValueId(UUID.randomUUID());

        assertThatThrownBy(() -> optionGroupDataMapper.toOptionValue(null, valueId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("AddOptionValueCommand cannot be null");
    }

    @Test
    @DisplayName("toOptionValue: 정상 매핑")
    void toOptionValue_Success() {
        OptionValueId valueId = new OptionValueId(UUID.randomUUID());
        AddOptionValueCommand command = AddOptionValueCommand.builder()
                .value("RED")
                .displayName("빨강")
                .sortOrder(1)
                .build();

        OptionValue optionValue = optionGroupDataMapper.toOptionValue(command, valueId);

        assertThat(optionValue).isNotNull();
        assertThat(optionValue.getId()).isEqualTo(valueId);
        assertThat(optionValue.getValue()).isEqualTo("RED");
        assertThat(optionValue.getDisplayName()).isEqualTo("빨강");
        assertThat(optionValue.getSortOrder()).isEqualTo(1);
        assertThat(optionValue.getStatus()).isEqualTo(OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("toAddOptionValueResult: OptionValue가 null이면 NullPointerException")
    void toAddOptionValueResult_NullOptionValue_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toAddOptionValueResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionValue cannot be null");
    }

    @Test
    @DisplayName("toAddOptionValueResult: OptionValue ID가 null이면 NullPointerException")
    void toAddOptionValueResult_NullOptionValueId_ThrowsNpe() {
        OptionValue optionValueWithoutId = OptionValue.builder()
                .value("BLUE")
                .displayName("파랑")
                .sortOrder(0)
                .build();

        assertThatThrownBy(() -> optionGroupDataMapper.toAddOptionValueResult(optionValueWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionValue ID cannot be null");
    }

    @Test
    @DisplayName("toAddOptionValueResult: 정상 매핑")
    void toAddOptionValueResult_Success() {
        UUID valueId = UUID.randomUUID();
        OptionValue optionValue = OptionValue.reconstitute(
                new OptionValueId(valueId),
                "LARGE",
                "라지",
                2,
                OptionStatus.ACTIVE
        );

        AddOptionValueResult result = optionGroupDataMapper.toAddOptionValueResult(optionValue);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(valueId);
        assertThat(result.value()).isEqualTo("LARGE");
    }

    @Test
    @DisplayName("toUpdateOptionValueResult: OptionValue가 null이면 NullPointerException")
    void toUpdateOptionValueResult_NullOptionValue_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toUpdateOptionValueResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionValue cannot be null");
    }

    @Test
    @DisplayName("toUpdateOptionValueResult: OptionValue ID가 null이면 NullPointerException")
    void toUpdateOptionValueResult_NullOptionValueId_ThrowsNpe() {
        OptionValue optionValueWithoutId = OptionValue.builder()
                .value("X")
                .displayName("엑스")
                .sortOrder(0)
                .build();

        assertThatThrownBy(() -> optionGroupDataMapper.toUpdateOptionValueResult(optionValueWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionValue ID cannot be null");
    }

    @Test
    @DisplayName("toUpdateOptionValueResult: 정상 매핑")
    void toUpdateOptionValueResult_Success() {
        UUID valueId = UUID.randomUUID();
        OptionValue optionValue = OptionValue.reconstitute(
                new OptionValueId(valueId),
                "MEDIUM",
                "미디엄",
                5,
                OptionStatus.INACTIVE
        );

        UpdateOptionValueResult result = optionGroupDataMapper.toUpdateOptionValueResult(optionValue);

        assertThat(result.id()).isEqualTo(valueId);
        assertThat(result.value()).isEqualTo("MEDIUM");
        assertThat(result.displayName()).isEqualTo("미디엄");
        assertThat(result.sortOrder()).isEqualTo(5);
        assertThat(result.status()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("toDeleteOptionValueResult: OptionValue가 null이면 NullPointerException")
    void toDeleteOptionValueResult_NullOptionValue_ThrowsNpe() {
        assertThatThrownBy(() -> optionGroupDataMapper.toDeleteOptionValueResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("OptionValue cannot be null");
    }

    @Test
    @DisplayName("toDeleteOptionValueResult: 정상 매핑")
    void toDeleteOptionValueResult_Success() {
        UUID valueId = UUID.randomUUID();
        OptionValue optionValue = OptionValue.reconstitute(
                new OptionValueId(valueId),
                "SMALL",
                "스몰",
                0,
                OptionStatus.DELETED
        );

        DeleteOptionValueResult result = optionGroupDataMapper.toDeleteOptionValueResult(optionValue);

        assertThat(result.id()).isEqualTo(valueId);
        assertThat(result.value()).isEqualTo("SMALL");
    }
}
