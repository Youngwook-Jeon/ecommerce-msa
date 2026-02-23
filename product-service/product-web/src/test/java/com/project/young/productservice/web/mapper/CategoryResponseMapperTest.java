package com.project.young.productservice.web.mapper;

import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import com.project.young.productservice.web.converter.CategoryStatusWebConverter;
import com.project.young.productservice.web.dto.CreateCategoryResponse;
import com.project.young.productservice.web.dto.DeleteCategoryResponse;
import com.project.young.productservice.web.dto.UpdateCategoryResponse;
import com.project.young.productservice.web.message.CategoryResponseMessageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryResponseMapperTest {

    private CategoryResponseMapper categoryResponseMapper;

    @BeforeEach
    void setUp() {
        categoryResponseMapper = new CategoryResponseMapper(
                new CategoryResponseMessageFactory(),
                new CategoryStatusWebConverter()
        );
    }

    @Nested
    @DisplayName("toCreateCategoryResponse")
    class ToCreateCategoryResponseTests {

        @Test
        @DisplayName("result가 null이면 NullPointerException")
        void nullResult_ThrowsNullPointerException() {
            assertThatThrownBy(() -> categoryResponseMapper.toCreateCategoryResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("CreateCategoryResult cannot be null");
        }

        @Test
        @DisplayName("정상 매핑")
        void success() {
            CreateCategoryResult result = new CreateCategoryResult(1L, "전자제품");

            CreateCategoryResponse response = categoryResponseMapper.toCreateCategoryResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("전자제품");
            assertThat(response.message()).isEqualTo("Category created successfully");
        }
    }

    @Nested
    @DisplayName("toUpdateCategoryResponse")
    class ToUpdateCategoryResponseTests {

        @Test
        @DisplayName("result가 null이면 NullPointerException")
        void nullResult_ThrowsNullPointerException() {
            assertThatThrownBy(() -> categoryResponseMapper.toUpdateCategoryResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("UpdateCategoryResult cannot be null");
        }

        @Test
        @DisplayName("parentId 없을 때 정상 매핑")
        void withoutParentId_Success() {
            UpdateCategoryResult result = new UpdateCategoryResult(
                    1L, "전자제품", null, CategoryStatus.ACTIVE);

            UpdateCategoryResponse response = categoryResponseMapper.toUpdateCategoryResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("전자제품");
            assertThat(response.parentId()).isNull();
            assertThat(response.status()).isEqualTo("ACTIVE");
            assertThat(response.message()).isEqualTo("Category updated successfully");
        }

        @Test
        @DisplayName("parentId 있을 때 정상 매핑")
        void withParentId_Success() {
            UpdateCategoryResult result = new UpdateCategoryResult(
                    2L, "스마트폰", 1L, CategoryStatus.ACTIVE);

            UpdateCategoryResponse response = categoryResponseMapper.toUpdateCategoryResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(2L);
            assertThat(response.name()).isEqualTo("스마트폰");
            assertThat(response.parentId()).isEqualTo(1L);
            assertThat(response.status()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("toDeleteCategoryResponse")
    class ToDeleteCategoryResponseTests {

        @Test
        @DisplayName("result가 null이면 NullPointerException")
        void nullResult_ThrowsNullPointerException() {
            assertThatThrownBy(() -> categoryResponseMapper.toDeleteCategoryResponse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("DeleteCategoryResult cannot be null");
        }

        @Test
        @DisplayName("정상 매핑")
        void success() {
            DeleteCategoryResult result = new DeleteCategoryResult(1L, "전자제품");

            DeleteCategoryResponse response = categoryResponseMapper.toDeleteCategoryResponse(result);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("전자제품");
            assertThat(response.message()).isEqualTo("Category deleted successfully");
        }
    }
}