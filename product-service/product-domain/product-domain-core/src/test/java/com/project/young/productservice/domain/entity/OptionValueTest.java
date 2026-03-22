package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.OptionValueId;
import com.project.young.productservice.domain.exception.OptionDomainException;
import com.project.young.productservice.domain.valueobject.OptionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OptionValueTest {

    private static OptionValueId id() {
        return new OptionValueId(UUID.randomUUID());
    }

    @Test
    @DisplayName("builder: value가 blank면 예외")
    void builder_BlankValue_Throws() {
        assertThatThrownBy(() -> OptionValue.builder().value(" ").displayName("표시").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option value string cannot be null or blank");
    }

    @Test
    @DisplayName("builder: displayName이 blank면 예외")
    void builder_BlankDisplayName_Throws() {
        assertThatThrownBy(() -> OptionValue.builder().value("RED").displayName(" ").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option value display name cannot be null or blank");
    }

    @Test
    @DisplayName("builder: value가 100자 초과면 예외")
    void builder_ValueTooLong_Throws() {
        String longVal = "A".repeat(101);
        assertThatThrownBy(() -> OptionValue.builder().value(longVal).displayName("표시").build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("100 characters or less");
    }

    @Test
    @DisplayName("builder: status가 null이면 예외")
    void builder_NullStatus_Throws() {
        assertThatThrownBy(() -> OptionValue.builder()
                .value("RED")
                .displayName("빨강")
                .status(null)
                .build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Option status cannot be null");
    }

    @Test
    @DisplayName("builder: 초기 status가 DELETED이면 예외")
    void builder_InitialStatusDeleted_Throws() {
        assertThatThrownBy(() -> OptionValue.builder()
                .value("RED")
                .displayName("빨강")
                .status(OptionStatus.DELETED)
                .build())
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("must not be created with DELETED status");
    }

    @Test
    @DisplayName("builder: 유효한 값으로 생성 성공")
    void builder_Success() {
        OptionValueId vid = id();
        OptionValue v = OptionValue.builder()
                .id(vid)
                .value("LARGE")
                .displayName("라지")
                .sortOrder(2)
                .status(OptionStatus.INACTIVE)
                .build();

        assertThat(v.getId()).isEqualTo(vid);
        assertThat(v.getValue()).isEqualTo("LARGE");
        assertThat(v.getDisplayName()).isEqualTo("라지");
        assertThat(v.getSortOrder()).isEqualTo(2);
        assertThat(v.getStatus()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("isDeleted: DELETED만 true")
    void isDeleted_OnlyDeleted() {
        assertThat(OptionValue.reconstitute(id(), "V", "표시", 0, OptionStatus.DELETED).isDeleted()).isTrue();
        assertThat(OptionValue.reconstitute(id(), "V", "표시", 0, OptionStatus.ACTIVE).isDeleted()).isFalse();
    }

    @Test
    @DisplayName("changeValue: 삭제된 값은 변경 불가")
    void changeValue_Deleted_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.DELETED);

        assertThatThrownBy(() -> v.changeValue("BLUE"))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option value");
    }

    @Test
    @DisplayName("changeValue: blank면 예외")
    void changeValue_Blank_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        assertThatThrownBy(() -> v.changeValue(" "))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("cannot be null or blank");
    }

    @Test
    @DisplayName("changeValue: 성공")
    void changeValue_Success() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        v.changeValue("CRIMSON");

        assertThat(v.getValue()).isEqualTo("CRIMSON");
    }

    @Test
    @DisplayName("changeDisplayName: 삭제된 값은 변경 불가")
    void changeDisplayName_Deleted_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.DELETED);

        assertThatThrownBy(() -> v.changeDisplayName("새표시"))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option value");
    }

    @Test
    @DisplayName("changeDisplayName: 성공")
    void changeDisplayName_Success() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        v.changeDisplayName("진한 빨강");

        assertThat(v.getDisplayName()).isEqualTo("진한 빨강");
    }

    @Test
    @DisplayName("changeSortOrder: 삭제된 값은 변경 불가")
    void changeSortOrder_Deleted_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.DELETED);

        assertThatThrownBy(() -> v.changeSortOrder(10))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option value");
    }

    @Test
    @DisplayName("changeSortOrder: 성공")
    void changeSortOrder_Success() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        v.changeSortOrder(99);

        assertThat(v.getSortOrder()).isEqualTo(99);
    }

    @Test
    @DisplayName("changeStatus: 삭제된 값은 변경 불가")
    void changeStatus_Deleted_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.DELETED);

        assertThatThrownBy(() -> v.changeStatus(OptionStatus.ACTIVE))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("deleted option value");
    }

    @Test
    @DisplayName("changeStatus: null이면 예외")
    void changeStatus_Null_Throws() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        assertThatThrownBy(() -> v.changeStatus(null))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Invalid status provided for update");
    }

    @Test
    @DisplayName("changeStatus: ACTIVE -> INACTIVE 성공")
    void changeStatus_ActiveToInactive_Success() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        v.changeStatus(OptionStatus.INACTIVE);

        assertThat(v.getStatus()).isEqualTo(OptionStatus.INACTIVE);
    }

    @Test
    @DisplayName("markAsDeleted: 멱등적으로 DELETED 설정")
    void markAsDeleted_Idempotent() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.ACTIVE);

        v.markAsDeleted();
        v.markAsDeleted();

        assertThat(v.getStatus()).isEqualTo(OptionStatus.DELETED);
        assertThat(v.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("reconstitute: DELETED 상태로 복원 가능")
    void reconstitute_DeletedStatus() {
        OptionValue v = OptionValue.reconstitute(id(), "RED", "빨강", 0, OptionStatus.DELETED);

        assertThat(v.isDeleted()).isTrue();
    }
}
