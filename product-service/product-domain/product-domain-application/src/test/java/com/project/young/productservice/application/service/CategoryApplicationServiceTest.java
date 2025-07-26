
package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.*;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryApplicationServiceTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryDomainService categoryDomainService;
    @Mock
    private CategoryDataMapper categoryDataMapper;

    @InjectMocks
    private CategoryApplicationService categoryApplicationService;

    @Nested
    @DisplayName("카테고리 생성 테스트")
    class CreateCategoryTests {

        @Test
        @DisplayName("부모 카테고리가 있는 카테고리 생성 성공")
        void createCategory_WithParent_Success() {
            // Given
            Long parentId = 1L;
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(parentId)
                    .build();

            CategoryId parentCategoryId = new CategoryId(parentId);
            Category categoryToSave = Category.builder()
                    .name("새 카테고리")
                    .parentId(parentCategoryId)
                    .build();

            Category savedCategory = Category.builder()
                    .categoryId(new CategoryId(10L))
                    .name("새 카테고리")
                    .parentId(parentCategoryId)
                    .build();

            CreateCategoryResponse expectedResponse = new CreateCategoryResponse(
                    "새 카테고리",
                    "Category 새 카테고리 created successfully."
            );

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryRepository.existsById(parentCategoryId)).thenReturn(true);
            when(categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)).thenReturn(true);
            when(categoryDataMapper.toCategory(command, parentCategoryId)).thenReturn(categoryToSave);
            when(categoryRepository.save(categoryToSave)).thenReturn(savedCategory);
            when(categoryDataMapper.toCreateCategoryResponse(eq(savedCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            CreateCategoryResponse response = categoryApplicationService.createCategory(command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("새 카테고리");
            assertThat(response.message()).contains("created successfully");

            verify(categoryDomainService).isCategoryNameUnique("새 카테고리");
            verify(categoryRepository).existsById(parentCategoryId);
            verify(categoryDomainService).isParentDepthLessThanLimit(parentCategoryId);
            verify(categoryRepository).save(categoryToSave);
        }

        @Test
        @DisplayName("최상위 카테고리 생성 성공")
        void createCategory_WithoutParent_Success() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("최상위 카테고리")
                    .parentId(null)
                    .build();

            Category categoryToSave = Category.builder()
                    .name("최상위 카테고리")
                    .parentId(null)
                    .build();

            Category savedCategory = Category.builder()
                    .categoryId(new CategoryId(10L))
                    .name("최상위 카테고리")
                    .parentId(null)
                    .build();

            CreateCategoryResponse expectedResponse = new CreateCategoryResponse(
                    "최상위 카테고리",
                    "Category 최상위 카테고리 created successfully."
            );

            when(categoryDomainService.isCategoryNameUnique("최상위 카테고리")).thenReturn(true);
            when(categoryDataMapper.toCategory(command, null)).thenReturn(categoryToSave);
            when(categoryRepository.save(categoryToSave)).thenReturn(savedCategory);
            when(categoryDataMapper.toCreateCategoryResponse(eq(savedCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            CreateCategoryResponse response = categoryApplicationService.createCategory(command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("최상위 카테고리");

            verify(categoryDomainService).isCategoryNameUnique("최상위 카테고리");
            verify(categoryRepository, never()).existsById(any(CategoryId.class));
            verify(categoryDomainService, never()).isParentDepthLessThanLimit(any(CategoryId.class));
        }

        @Test
        @DisplayName("중복된 카테고리 이름으로 생성 실패")
        void createCategory_DuplicateName_ThrowsException() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("중복 카테고리")
                    .parentId(null)
                    .build();

            when(categoryDomainService.isCategoryNameUnique("중복 카테고리")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(DuplicateCategoryNameException.class)
                    .hasMessageContaining("already exists");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("존재하지 않는 부모 카테고리로 생성 실패")
        void createCategory_ParentNotFound_ThrowsException() {
            // Given
            Long parentId = 999L;
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(parentId)
                    .build();

            CategoryId parentCategoryId = new CategoryId(parentId);

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryRepository.existsById(parentCategoryId)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Parent category with id")
                    .hasMessageContaining("not found");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("깊이 제한 초과로 생성 실패")
        void createCategory_DepthLimitExceeded_ThrowsException() {
            // Given
            Long parentId = 1L;
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(parentId)
                    .build();

            CategoryId parentCategoryId = new CategoryId(parentId);

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryRepository.existsById(parentCategoryId)).thenReturn(true);
            when(categoryDomainService.isParentDepthLessThanLimit(parentCategoryId)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("depth limit exceeded");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("저장 후 ID가 할당되지 않아 실패")
        void createCategory_IdNotAssigned_ThrowsException() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build();

            Category categoryToSave = Category.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build();

            Category savedCategoryWithoutId = Category.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build(); // ID가 null인 상태

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryDataMapper.toCategory(command, null)).thenReturn(categoryToSave);
            when(categoryRepository.save(categoryToSave)).thenReturn(savedCategoryWithoutId);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Failed to assign ID");

            verify(categoryDataMapper, never()).toCreateCategoryResponse(any(), anyString());
        }
    }

    @Nested
    @DisplayName("카테고리 수정 테스트")
    class UpdateCategoryTests {

        @Test
        @DisplayName("카테고리 이름 수정 성공")
        void updateCategory_ChangeName_Success() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("수정된 카테고리")
                    .parentId(null)
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("기존 카테고리")
                    .parentId(null)
                    .build();

            Category updatedCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("수정된 카테고리")
                    .parentId(null)
                    .build();

            UpdateCategoryResponse expectedResponse = new UpdateCategoryResponse(
                    "수정된 카테고리",
                    "Category 수정된 카테고리 updated successfully."
            );

            when(categoryRepository.findById(categoryIdVo)).thenReturn(Optional.of(existingCategory));
            when(categoryDomainService.isCategoryNameUniqueForUpdate("수정된 카테고리", categoryIdVo)).thenReturn(true);
            when(categoryRepository.save(existingCategory)).thenReturn(updatedCategory);
            when(categoryDataMapper.toUpdateCategoryResponse(eq(updatedCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            UpdateCategoryResponse response = categoryApplicationService.updateCategory(categoryId, command);

            // Then
            assertThat(response).isNotNull();
//            assertThat(response.).isEqualTo(categoryId);
            assertThat(response.name()).isEqualTo("수정된 카테고리");
            assertThat(response.message()).contains("updated successfully");

            verify(categoryRepository).save(existingCategory);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 수정 실패")
        void updateCategory_CategoryNotFound_ThrowsException() {
            // Given
            Long categoryId = 999L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("수정된 카테고리")
                    .build();

            when(categoryRepository.findById(categoryIdVo)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id")
                    .hasMessageContaining("not found");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("null ID로 수정 시도 실패")
        void updateCategory_NullId_ThrowsException() {
            // Given
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("수정된 카테고리")
                    .build();

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(null, command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category ID for update cannot be null");
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 테스트")
    class DeleteCategoryTests {

        @Test
        @DisplayName("카테고리 및 하위 카테고리 삭제 성공")
        void deleteCategory_WithSubtree_Success() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            Category rootCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("루트 카테고리")
                    .parentId(null)
                    .build();

            Category childCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("자식 카테고리")
                    .parentId(categoryIdVo)
                    .build();

            List<Category> categoriesToDelete = List.of(rootCategory, childCategory);
            List<Category> savedCategories = List.of(rootCategory, childCategory);

            DeleteCategoryResponse expectedResponse = new DeleteCategoryResponse(
                    categoryId,
                    "Category 루트 카테고리 (ID: 1) marked as deleted successfully."
            );

            when(categoryRepository.findAllSubTreeById(categoryIdVo)).thenReturn(categoriesToDelete);
            when(categoryRepository.saveAll(categoriesToDelete)).thenReturn(savedCategories);

            // When
            DeleteCategoryResponse response = categoryApplicationService.deleteCategory(categoryId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(categoryId);
            assertThat(response.message()).contains("marked as deleted successfully");

            verify(categoryRepository).saveAll(categoriesToDelete);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 삭제 실패")
        void deleteCategory_CategoryNotFound_ThrowsException() {
            // Given
            Long categoryId = 999L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            when(categoryRepository.findAllSubTreeById(categoryIdVo)).thenReturn(List.of());

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.deleteCategory(categoryId))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id")
                    .hasMessageContaining("not found");

            verify(categoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("null ID로 삭제 시도 실패")
        void deleteCategory_NullId_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.deleteCategory(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category ID for delete cannot be null");
        }
    }
}