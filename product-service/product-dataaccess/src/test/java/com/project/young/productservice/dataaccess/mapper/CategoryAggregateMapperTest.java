package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CategoryAggregateMapperTest {

    private final CategoryAggregateMapper mapper = new CategoryAggregateMapper();

    @Test
    @DisplayName("toCategory: 부모 없이 매핑")
    void toCategory_WithoutParent() {
        CategoryEntity entity = CategoryEntity.builder()
                .id(1L)
                .name("전자제품")
                .status(CategoryStatusEntity.ACTIVE)
                .build();

        Category domain = mapper.toCategory(entity);

        assertThat(domain.getId()).isEqualTo(new CategoryId(1L));
        assertThat(domain.getName()).isEqualTo("전자제품");
        assertThat(domain.getParentId()).isEmpty();
        assertThat(domain.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
    }

    @Test
    @DisplayName("toCategory: 부모 참조 포함 매핑")
    void toCategory_WithParent() {
        CategoryEntity parent = CategoryEntity.builder()
                .id(1L)
                .name("루트")
                .status(CategoryStatusEntity.ACTIVE)
                .build();
        CategoryEntity entity = CategoryEntity.builder()
                .id(2L)
                .name("자식")
                .status(CategoryStatusEntity.INACTIVE)
                .parent(parent)
                .build();

        Category domain = mapper.toCategory(entity);

        assertThat(domain.getId()).isEqualTo(new CategoryId(2L));
        assertThat(domain.getParentId()).contains(new CategoryId(1L));
        assertThat(domain.getStatus()).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    @DisplayName("toCategory: null 엔티티면 NullPointerException")
    void toCategory_Null_Throws() {
        assertThatThrownBy(() -> mapper.toCategory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("categoryEntity");
    }

    @Test
    @DisplayName("toCategory: id가 null이면 NullPointerException")
    void toCategory_NullId_Throws() {
        CategoryEntity entity = CategoryEntity.builder()
                .id(null)
                .name("x")
                .status(CategoryStatusEntity.ACTIVE)
                .build();

        assertThatThrownBy(() -> mapper.toCategory(entity))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must not be null");
    }
}
