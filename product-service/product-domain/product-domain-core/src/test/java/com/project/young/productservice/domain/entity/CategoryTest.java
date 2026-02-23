package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CategoryTest {

    @Test
    @DisplayName("builder: name이 blank면 예외")
    void builder_BlankName_Throws() {
        assertThatThrownBy(() -> Category.builder().name(" ").build())
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("Category name cannot be blank");
    }

    @Test
    @DisplayName("builder: name 길이가 2~50 범위를 벗어나면 예외")
    void builder_InvalidNameLength_Throws() {
        assertThatThrownBy(() -> Category.builder().name("A").build())
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("between 2 and 50");

        String longName = "A".repeat(51);
        assertThatThrownBy(() -> Category.builder().name(longName).build())
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("between 2 and 50");
    }

    @Test
    @DisplayName("builder: 초기 status가 ACTIVE가 아니면 예외")
    void builder_InitialStatusNotActive_Throws() {
        assertThatThrownBy(() -> Category.builder()
                .name("전자제품")
                .status(CategoryStatus.INACTIVE)
                .build())
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("initial ACTIVE");
    }

    @Test
    @DisplayName("changeName: 삭제된 카테고리는 이름 변경 불가")
    void changeName_Deleted_Throws() {
        Category category = Category.reconstitute(new CategoryId(1L), "전자제품", null, CategoryStatus.DELETED);

        assertThatThrownBy(() -> category.changeName("바뀐이름"))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("deleted category");
    }

    @Test
    @DisplayName("changeName: 유효한 이름으로 변경 성공")
    void changeName_Success() {
        Category category = Category.reconstitute(new CategoryId(1L), "전자제품", null, CategoryStatus.ACTIVE);

        category.changeName("가전");

        assertThat(category.getName()).isEqualTo("가전");
    }

    @Test
    @DisplayName("changeParent: 자기 자신을 부모로 설정하면 예외")
    void changeParent_SelfParent_Throws() {
        CategoryId id = new CategoryId(1L);
        Category category = Category.reconstitute(id, "전자제품", null, CategoryStatus.ACTIVE);

        assertThatThrownBy(() -> category.changeParent(id))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    @DisplayName("changeStatus: DELETED 상태는 변경 불가")
    void changeStatus_FromDeleted_Throws() {
        Category category = Category.reconstitute(new CategoryId(1L), "전자제품", null, CategoryStatus.DELETED);

        assertThatThrownBy(() -> category.changeStatus(CategoryStatus.ACTIVE))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("deleted");
    }

    @Test
    @DisplayName("markAsDeleted: 멱등적으로 DELETED 설정")
    void markAsDeleted_Idempotent() {
        Category category = Category.reconstitute(new CategoryId(1L), "전자제품", null, CategoryStatus.ACTIVE);

        category.markAsDeleted();
        category.markAsDeleted();

        assertThat(category.getStatus()).isEqualTo(CategoryStatus.DELETED);
        assertThat(category.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("assignId: 양수만 허용, 이미 ID가 있으면 예외")
    void assignId_Rules() {
        Category category = Category.builder()
                .name("전자제품")
                .status(CategoryStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> category.assignId(0L))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("must be positive");

        category.assignId(10L);
        assertThat(category.getId()).isEqualTo(new CategoryId(10L));

        assertThatThrownBy(() -> category.assignId(11L))
                .isInstanceOf(CategoryDomainException.class)
                .hasMessageContaining("already assigned");
    }
}