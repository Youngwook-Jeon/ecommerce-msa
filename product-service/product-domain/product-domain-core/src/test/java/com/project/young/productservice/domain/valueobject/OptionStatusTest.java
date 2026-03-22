package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.OptionDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OptionStatusTest {

    @Test
    @DisplayName("canTransitionTo: null이면 false")
    void canTransitionTo_Null_False() {
        assertThat(OptionStatus.ACTIVE.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: 동일 상태는 true")
    void canTransitionTo_SameStatus_True() {
        assertThat(OptionStatus.ACTIVE.canTransitionTo(OptionStatus.ACTIVE)).isTrue();
        assertThat(OptionStatus.DELETED.canTransitionTo(OptionStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: DELETED에서 다른 상태로 전이 불가")
    void canTransitionTo_FromDeleted_ToOther_False() {
        assertThat(OptionStatus.DELETED.canTransitionTo(OptionStatus.ACTIVE)).isFalse();
        assertThat(OptionStatus.DELETED.canTransitionTo(OptionStatus.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: ACTIVE <-> INACTIVE, ACTIVE/INACTIVE -> DELETED 허용")
    void canTransitionTo_AllowedTransitions() {
        assertThat(OptionStatus.ACTIVE.canTransitionTo(OptionStatus.INACTIVE)).isTrue();
        assertThat(OptionStatus.INACTIVE.canTransitionTo(OptionStatus.ACTIVE)).isTrue();

        assertThat(OptionStatus.ACTIVE.canTransitionTo(OptionStatus.DELETED)).isTrue();
        assertThat(OptionStatus.INACTIVE.canTransitionTo(OptionStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("isDeleted: DELETED만 true")
    void isDeleted_OnlyDeleted_True() {
        assertThat(OptionStatus.DELETED.isDeleted()).isTrue();
        assertThat(OptionStatus.ACTIVE.isDeleted()).isFalse();
        assertThat(OptionStatus.INACTIVE.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("isActive: ACTIVE만 true")
    void isActive_OnlyActive_True() {
        assertThat(OptionStatus.ACTIVE.isActive()).isTrue();
        assertThat(OptionStatus.INACTIVE.isActive()).isFalse();
        assertThat(OptionStatus.DELETED.isActive()).isFalse();
    }

    @Test
    @DisplayName("fromString: null/blank면 예외")
    void fromString_NullOrBlank_Throws() {
        assertThatThrownBy(() -> OptionStatus.fromString(null))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("cannot be null or empty");

        assertThatThrownBy(() -> OptionStatus.fromString(" "))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("fromString: 유효한 값이면 대소문자 무시하고 파싱")
    void fromString_ParsesCaseInsensitive() {
        assertThat(OptionStatus.fromString("active")).isEqualTo(OptionStatus.ACTIVE);
        assertThat(OptionStatus.fromString("INACTIVE")).isEqualTo(OptionStatus.INACTIVE);
        assertThat(OptionStatus.fromString("deleted")).isEqualTo(OptionStatus.DELETED);
    }

    @Test
    @DisplayName("fromString: 유효하지 않으면 예외")
    void fromString_Invalid_Throws() {
        assertThatThrownBy(() -> OptionStatus.fromString("UNKNOWN"))
                .isInstanceOf(OptionDomainException.class)
                .hasMessageContaining("Invalid option status");
    }

    @Test
    @DisplayName("getDescription: 각 상태별 설명 반환")
    void getDescription_ReturnsDescription() {
        assertThat(OptionStatus.ACTIVE.getDescription()).isEqualTo("활성");
        assertThat(OptionStatus.INACTIVE.getDescription()).isEqualTo("비활성");
        assertThat(OptionStatus.DELETED.getDescription()).isEqualTo("삭제됨");
    }
}
