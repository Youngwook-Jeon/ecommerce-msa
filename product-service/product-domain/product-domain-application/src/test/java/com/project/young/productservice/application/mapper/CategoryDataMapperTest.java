package com.project.young.productservice.application.mapper;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CategoryDataMapperTest {

    private CategoryDataMapper categoryDataMapper;

    @BeforeEach
    void setUp() {
        categoryDataMapper = new CategoryDataMapper();
    }

    @Test
    @DisplayName("toCreateCategoryResult: Category가 null이면 NullPointerException")
    void toCreateCategoryResult_NullCategory_ThrowsNpe() {
        assertThatThrownBy(() -> categoryDataMapper.toCreateCategoryResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Category cannot be null");
    }

    @Test
    @DisplayName("toCreateCategoryResult: Category ID가 null이면 NullPointerException")
    void toCreateCategoryResult_NullCategoryId_ThrowsNpe() {
        Category categoryWithoutId = Category.builder()
                .name("전자제품")
                .parentId(null)
                .status(CategoryStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> categoryDataMapper.toCreateCategoryResult(categoryWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Category ID cannot be null");
    }

    @Test
    @DisplayName("toCreateCategoryResult: 정상 매핑")
    void toCreateCategoryResult_Success() {
        CategoryId id = new CategoryId(1L);
        Category category = Category.reconstitute(id, "전자제품", null, CategoryStatus.ACTIVE);

        CreateCategoryResult result = categoryDataMapper.toCreateCategoryResult(category);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("전자제품");
    }

    @Test
    @DisplayName("toUpdateCategoryResult: Category가 null이면 NullPointerException")
    void toUpdateCategoryResult_NullCategory_ThrowsNpe() {
        assertThatThrownBy(() -> categoryDataMapper.toUpdateCategoryResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Category cannot be null");
    }

    @Test
    @DisplayName("toUpdateCategoryResult: Category ID가 null이면 NullPointerException")
    void toUpdateCategoryResult_NullCategoryId_ThrowsNpe() {
        // Given
        Category categoryWithoutId = Category.builder()
                .name("전자제품")
                .parentId(null)
                .status(CategoryStatus.ACTIVE)
                .build();

        // When & Then
        assertThatThrownBy(() -> categoryDataMapper.toUpdateCategoryResult(categoryWithoutId))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Category ID cannot be null");
    }

    @Test
    @DisplayName("toUpdateCategoryResult: parentId가 없으면 null로 매핑")
    void toUpdateCategoryResult_WithoutParentId_Success() {
        // Given
        CategoryId id = new CategoryId(10L);
        Category category = Category.reconstitute(id, "루트", null, CategoryStatus.ACTIVE);

        // When
        UpdateCategoryResult result = categoryDataMapper.toUpdateCategoryResult(category);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("루트");
        assertThat(result.parentId()).isNull();
        assertThat(result.status()).isEqualTo(CategoryStatus.ACTIVE);
    }

    @Test
    @DisplayName("toUpdateCategoryResult: parentId가 있으면 값으로 매핑")
    void toUpdateCategoryResult_WithParentId_Success() {
        CategoryId id = new CategoryId(11L);
        CategoryId parentId = new CategoryId(1L);
        Category category = Category.reconstitute(id, "자식", parentId, CategoryStatus.INACTIVE);

        UpdateCategoryResult result = categoryDataMapper.toUpdateCategoryResult(category);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(11L);
        assertThat(result.name()).isEqualTo("자식");
        assertThat(result.parentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(CategoryStatus.INACTIVE);
    }

    @Test
    @DisplayName("toDeleteCategoryResult: Category가 null이면 NullPointerException")
    void toDeleteCategoryResult_NullCategory_ThrowsNpe() {
        assertThatThrownBy(() -> categoryDataMapper.toDeleteCategoryResult(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Category cannot be null");
    }

    @Test
    @DisplayName("toDeleteCategoryResult: 정상 매핑")
    void toDeleteCategoryResult_Success() {
        CategoryId id = new CategoryId(100L);
        Category category = Category.reconstitute(id, "삭제대상", null, CategoryStatus.DELETED);

        DeleteCategoryResult result = categoryDataMapper.toDeleteCategoryResult(category);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.name()).isEqualTo("삭제대상");
    }

    @Test
    @DisplayName("toCategory: command가 null이면 NullPointerException")
    void toCategory_NullCommand_ThrowsNpe() {
        assertThatThrownBy(() -> categoryDataMapper.toCategory(null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("CreateCategoryCommand cannot be null");
    }

    @Test
    @DisplayName("toCategory: parentId가 있으면 Category에 반영")
    void toCategory_WithParentId_Success() {
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name("노트북")
                .parentId(1L)
                .build();

        CategoryId parentId = new CategoryId(1L);

        Category category = categoryDataMapper.toCategory(command, parentId);

        assertThat(category).isNotNull();
        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("노트북");
        assertThat(category.getParentId()).isPresent();
        assertThat(category.getParentId().get()).isEqualTo(parentId);
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
    }

    @Test
    @DisplayName("toCategory: parentId가 null이면 루트 카테고리로 매핑")
    void toCategory_WithoutParentId_Success() {
        CreateCategoryCommand command = CreateCategoryCommand.builder()
                .name("최상위")
                .parentId(null)
                .build();

        Category category = categoryDataMapper.toCategory(command, null);

        assertThat(category).isNotNull();
        assertThat(category.getId()).isNull();
        assertThat(category.getName()).isEqualTo("최상위");
        assertThat(category.getParentId()).isEmpty();
        assertThat(category.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
    }
}