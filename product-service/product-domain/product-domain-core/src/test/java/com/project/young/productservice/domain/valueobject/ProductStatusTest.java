package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.ProductDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductStatusTest {

    @Test
    @DisplayName("canTransitionTo: null이면 false")
    void canTransitionTo_Null_False() {
        assertThat(ProductStatus.ACTIVE.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: 동일 상태는 true")
    void canTransitionTo_SameStatus_True() {
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.ACTIVE)).isTrue();
        assertThat(ProductStatus.DELETED.canTransitionTo(ProductStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: DELETED에서 다른 상태로 전이 불가")
    void canTransitionTo_FromDeleted_ToOther_False() {
        assertThat(ProductStatus.DELETED.canTransitionTo(ProductStatus.ACTIVE)).isFalse();
        assertThat(ProductStatus.DELETED.canTransitionTo(ProductStatus.INACTIVE)).isFalse();
        assertThat(ProductStatus.DELETED.canTransitionTo(ProductStatus.DISCONTINUED)).isFalse();
        assertThat(ProductStatus.DELETED.canTransitionTo(ProductStatus.OUT_OF_STOCK)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: DELETED가 아니면 다른 상태로 전이 가능")
    void canTransitionTo_FromNonDeleted_True() {
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DELETED)).isTrue();
        assertThat(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.OUT_OF_STOCK)).isTrue();
        assertThat(ProductStatus.INACTIVE.canTransitionTo(ProductStatus.ACTIVE)).isTrue();
        assertThat(ProductStatus.DISCONTINUED.canTransitionTo(ProductStatus.DELETED)).isTrue();
    }

    @Test
    @DisplayName("isDeleted: DELETED만 true")
    void isDeleted_OnlyDeleted_True() {
        assertThat(ProductStatus.DELETED.isDeleted()).isTrue();
        assertThat(ProductStatus.ACTIVE.isDeleted()).isFalse();
        assertThat(ProductStatus.INACTIVE.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("isActive: ACTIVE만 true")
    void isActive_OnlyActive_True() {
        assertThat(ProductStatus.ACTIVE.isActive()).isTrue();
        assertThat(ProductStatus.INACTIVE.isActive()).isFalse();
        assertThat(ProductStatus.DELETED.isActive()).isFalse();
    }

    @Test
    @DisplayName("fromString: null/blank면 예외")
    void fromString_NullOrBlank_Throws() {
        assertThatThrownBy(() -> ProductStatus.fromString(null))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> ProductStatus.fromString(" "))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("fromString: 유효한 값이면 대소문자 무시하고 파싱")
    void fromString_ParsesCaseInsensitive() {
        assertThat(ProductStatus.fromString("active")).isEqualTo(ProductStatus.ACTIVE);
        assertThat(ProductStatus.fromString("DELETED")).isEqualTo(ProductStatus.DELETED);
        assertThat(ProductStatus.fromString("out_of_stock")).isEqualTo(ProductStatus.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("fromString: 유효하지 않으면 예외")
    void fromString_Invalid_Throws() {
        assertThatThrownBy(() -> ProductStatus.fromString("UNKNOWN"))
                .isInstanceOf(ProductDomainException.class)
                .hasMessageContaining("Invalid product status");
    }

    @Test
    @DisplayName("getDescription: 각 상태별 설명 반환")
    void getDescription_ReturnsDescription() {
        assertThat(ProductStatus.ACTIVE.getDescription()).isEqualTo("활성");
        assertThat(ProductStatus.DELETED.getDescription()).isEqualTo("삭제됨");
        assertThat(ProductStatus.OUT_OF_STOCK.getDescription()).isEqualTo("품절");
    }
}
