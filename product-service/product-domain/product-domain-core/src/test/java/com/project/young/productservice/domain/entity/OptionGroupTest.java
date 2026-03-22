package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.OptionGroupId;
import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OptionGroupTest {

    private static OptionGroupId groupId() {
        return new OptionGroupId(UUID.randomUUID());
    }

    private static OptionValueId valueId() {
        return new OptionValueId(UUID.randomUUID());
    }

    private static OptionGroup activeGroup(String name, String displayName, List<OptionValue> values) {
        return OptionGroup.reconstitute(groupId(), name, displayName, OptionStatus.ACTIVE, values);
    }

    private static OptionValue redValue() {
        return OptionValue.reconstitute(valueId(), "RED", "빨강", 0, OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("builder: name이 blank면 예외")
    void builder_BlankName_Throws() {
        assertThatThrownBy(() -> OptionGroup.builder().name(" ").displayName("색상").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option group name cannot be null or blank");
    }

    @Test
    @DisplayName("builder: displayName이 blank면 예외")
    void builder_BlankDisplayName_Throws() {
        assertThatThrownBy(() -> OptionGroup.builder().name("COLOR").displayName(" ").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option group display name cannot be null or blank");
    }

    @Test
    @DisplayName("builder: name이 100자 초과면 예외")
    void builder_NameTooLong_Throws() {
        String longName = "A".repeat(101);
        assertThatThrownBy(() -> OptionGroup.builder().name(longName).displayName("색상").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("100 characters or less");
    }

    @Test
    @DisplayName("builder: status가 null이면 예외")
    void builder_NullStatus_Throws() {
        assertThatThrownBy(() -> OptionGroup.builder()
                .name("COLOR")
                .displayName("색상")
                .status(null)
                .build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option status cannot be null");
    }

    @Test
    @DisplayName("builder: 초기 status가 DELETED이면 예외")
    void builder_InitialStatusDeleted_Throws() {
        assertThatThrownBy(() -> OptionGroup.builder()
                .name("COLOR")
                .displayName("색상")
                .status(OptionStatus.DELETED)
                .build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("must not be created with DELETED status");
    }

    @Test
    @DisplayName("builder: 유효한 값으로 생성 성공")
    void builder_Success() {
        OptionGroupId id = groupId();
        OptionGroup group = OptionGroup.builder()
                .id(id)
                .name("SIZE")
                .displayName("사이즈")
                .build();

        assertThat(group.getId()).isEqualTo(id);
        assertThat(group.getName()).isEqualTo("SIZE");
        assertThat(group.getDisplayName()).isEqualTo("사이즈");
        assertThat(group.getStatus()).isEqualTo(OptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("getOptionValues: 방어적 복사본이라 외부에서 add 불가")
    void getOptionValues_DefensiveCopy() {
        OptionGroup group = activeGroup("COLOR", "색상", new ArrayList<>());

        assertThatThrownBy(() -> group.getOptionValues().add(redValue()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getOptionValue: 없는 ID면 예외")
    void getOptionValue_NotFound_Throws() {
        OptionGroup group = activeGroup("COLOR", "색상", List.of(redValue()));

        assertThatThrownBy(() -> group.getOptionValue(valueId()))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option value not found in this group");
    }

    @Test
    @DisplayName("changeName: 삭제된 그룹은 변경 불가")
    void changeName_Deleted_Throws() {
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of());

        assertThatThrownBy(() -> group.changeName("NEW"))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("changeName: 유효한 이름으로 변경 성공")
    void changeName_Success() {
        OptionGroup group = activeGroup("COLOR", "색상", List.of());

        group.changeName("COLOR_NEW");

        assertThat(group.getName()).isEqualTo("COLOR_NEW");
    }

    @Test
    @DisplayName("changeDisplayName: 삭제된 그룹은 변경 불가")
    void changeDisplayName_Deleted_Throws() {
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of());

        assertThatThrownBy(() -> group.changeDisplayName("새표시"))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("changeStatus: 삭제된 그룹은 변경 불가")
    void changeStatus_Deleted_Throws() {
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of());

        assertThatThrownBy(() -> group.changeStatus(OptionStatus.INACTIVE))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("changeStatus: null이면 canTransitionTo가 false여서 예외")
    void changeStatus_Null_Throws() {
        OptionGroup group = activeGroup("COLOR", "색상", List.of());

        assertThatThrownBy(() -> group.changeStatus(null))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Invalid status provided for update");
    }

    @Test
    @DisplayName("changeStatus: ACTIVE -> INACTIVE 성공")
    void changeStatus_ActiveToInactive_Success() {
        OptionGroup group = activeGroup("COLOR", "색상", List.of());

        group.changeStatus(OptionStatus.INACTIVE);

        assertThat(group.getStatus()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("addOptionValue: 삭제된 그룹에는 추가 불가")
    void addOptionValue_DeletedGroup_Throws() {
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of());

        assertThatThrownBy(() -> group.addOptionValue(redValue()))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("addOptionValue: 동일 value(대소문자 무시) 중복이면 예외")
    void addOptionValue_DuplicateValueIgnoreCase_Throws() {
        OptionValue first = OptionValue.reconstitute(valueId(), "RED", "빨강", 0, OptionStatus.ACTIVE);
        OptionGroup group = activeGroup("COLOR", "색상", List.of(first));
        OptionValue duplicate = OptionValue.reconstitute(valueId(), "red", "다른표시", 1, OptionStatus.ACTIVE);

        assertThatThrownBy(() -> group.addOptionValue(duplicate))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("already exists in group");
    }

    @Test
    @DisplayName("addOptionValue: 성공")
    void addOptionValue_Success() {
        OptionGroup group = activeGroup("COLOR", "색상", new ArrayList<>());

        group.addOptionValue(redValue());

        assertThat(group.getOptionValues()).hasSize(1);
    }

    @Test
    @DisplayName("updateOptionValueDetails: 삭제된 그룹이면 예외")
    void updateOptionValueDetails_DeletedGroup_Throws() {
        OptionValue v = redValue();
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of(v));

        assertThatThrownBy(() -> group.updateOptionValueDetails(v.getId(), "X", "Y", 0, OptionStatus.INACTIVE))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("updateOptionValueDetails: 대상이 없으면 예외")
    void updateOptionValueDetails_NotFound_Throws() {
        OptionGroup group = activeGroup("COLOR", "색상", List.of(redValue()));

        assertThatThrownBy(() -> group.updateOptionValueDetails(valueId(), "X", "Y", 0, OptionStatus.INACTIVE))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option value not found in this group");
    }

    @Test
    @DisplayName("updateOptionValueDetails: 다른 값과 value 충돌(대소문자 무시)이면 예외")
    void updateOptionValueDetails_DuplicateValue_Throws() {
        OptionValue v1 = OptionValue.reconstitute(valueId(), "RED", "빨강", 0, OptionStatus.ACTIVE);
        OptionValue v2 = OptionValue.reconstitute(valueId(), "BLUE", "파랑", 1, OptionStatus.ACTIVE);
        OptionGroup group = activeGroup("COLOR", "색상", List.of(v1, v2));

        assertThatThrownBy(() -> group.updateOptionValueDetails(v2.getId(), "red", "파랑", 1, OptionStatus.ACTIVE))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("already exists in group");
    }

    @Test
    @DisplayName("updateOptionValueDetails: 필드 갱신 성공")
    void updateOptionValueDetails_Success() {
        OptionValue v = OptionValue.reconstitute(valueId(), "RED", "빨강", 0, OptionStatus.ACTIVE);
        OptionGroup group = activeGroup("COLOR", "색상", List.of(v));

        group.updateOptionValueDetails(v.getId(), "RED", "진한 빨강", 5, OptionStatus.INACTIVE);

        OptionValue updated = group.getOptionValue(v.getId());
        assertThat(updated.getDisplayName()).isEqualTo("진한 빨강");
        assertThat(updated.getSortOrder()).isEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("deleteOptionValue: 삭제된 그룹이면 예외")
    void deleteOptionValue_DeletedGroup_Throws() {
        OptionValue v = redValue();
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of(v));

        assertThatThrownBy(() -> group.deleteOptionValue(v.getId()))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option group");
    }

    @Test
    @DisplayName("deleteOptionValue: 소프트 삭제로 상태가 DELETED")
    void deleteOptionValue_SoftDeletesValue() {
        OptionValue v = redValue();
        OptionGroup group = activeGroup("COLOR", "색상", List.of(v));

        group.deleteOptionValue(v.getId());

        assertThat(group.getOptionValue(v.getId()).getStatus()).isEqualTo(OptionStatus.DELETED);
    }

    @Test
    @DisplayName("markAsDeleted: 그룹과 하위 옵션 값을 연쇄 삭제, 멱등")
    void markAsDeleted_CascadesAndIdempotent() {
        OptionValue v = redValue();
        OptionGroup group = activeGroup("COLOR", "색상", List.of(v));

        group.markAsDeleted();
        group.markAsDeleted();

        assertThat(group.getStatus()).isEqualTo(OptionStatus.DELETED);
        assertThat(group.getOptionValue(v.getId()).getStatus()).isEqualTo(OptionStatus.DELETED);
    }

    @Test
    @DisplayName("reconstitute: DELETED 상태로도 복원 가능 (빌더와 달리)")
    void reconstitute_AllowsDeletedStatus() {
        OptionGroup group = OptionGroup.reconstitute(groupId(), "COLOR", "색상", OptionStatus.DELETED, List.of());

        assertThat(group.isDeleted()).isTrue();
    }
}
