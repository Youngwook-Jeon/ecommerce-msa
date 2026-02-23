package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.CategoryDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CategoryStatusTest {

    @Test
    @DisplayName("canTransitionTo: null이면 false")
    void canTransitionTo_Null_False() {
        assertThat(CategoryStatus.ACTIVE.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: 동일 상태는 true")
    void canTransitionTo_SameStatus_True() {
        assertThat(CategoryStatus.ACTIVE.canTransitionTo(CategoryStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: DELETED에서 어떤 상태로도 전이 불가")
    void canTransitionTo_FromDeleted_False() {
        assertThat(CategoryStatus.DELETED.canTransitionTo(CategoryStatus.ACTIVE)).isFalse();
        assertThat(CategoryStatus.DELETED.canTransitionTo(CategoryStatus.INACTIVE)).isFalse();
        assertThat(CategoryStatus.DELETED.canTransitionTo(CategoryStatus.DELETED)).isTrue(); // same status
    }

    @Test
    @DisplayName("canTransitionTo: ACTIVE <-> INACTIVE 전이 가능, ACTIVE/INACTIVE -> DELETED 가능")
    void canTransitionTo_AllowedTransitions() {
        assertThat(CategoryStatus.ACTIVE.canTransitionTo(CategoryStatus.INACTIVE)).isTrue();
        assertThat(CategoryStatus.INACTIVE.canTransitionTo(CategoryStatus.ACTIVE)).isTrue();

        assertThat(CategoryStatus.ACTIVE.canTransitionTo(CategoryStatus.DELETED)).isTrue();
        assertThat(CategoryStatus.INACTIVE.canTransitionTo(CategoryStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("fromString: null/blank면 예외")
    void fromString_NullOrBlank_Throws() {
        assertThatThrownBy(() -> CategoryStatus.fromString(null))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> CategoryStatus.fromString(" "))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("fromString: 유효한 값이면 대소문자 무시하고 파싱")
    void fromString_ParsesCaseInsensitive() {
        assertThat(CategoryStatus.fromString("active")).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(CategoryStatus.fromString("INACTIVE")).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    @DisplayName("fromString: 유효하지 않으면 예외")
    void fromString_Invalid_Throws() {
        assertThatThrownBy(() -> CategoryStatus.fromString("UNKNOWN"))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("Invalid category status");
    }
}