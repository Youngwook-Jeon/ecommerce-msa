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
            Category parentCategory = Category.builder()
                    .categoryId(parentCategoryId)
                    .name("부모 카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

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
            when(categoryDomainService.validateParentCategory(parentCategoryId)).thenReturn(parentCategory);
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
            verify(categoryDomainService).validateParentCategory(parentCategoryId);
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
            verify(categoryDomainService, never()).validateParentCategory(any(CategoryId.class));
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
        @DisplayName("부모 카테고리 검증 실패로 생성 실패")
        void createCategory_ParentValidationFailed_ThrowsException() {
            // Given
            Long parentId = 999L;
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(parentId)
                    .build();

            CategoryId parentCategoryId = new CategoryId(parentId);

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryDomainService.validateParentCategory(parentCategoryId))
                    .thenThrow(new CategoryDomainException("Parent category with id 999 not found."));

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
            Category parentCategory = Category.builder()
                    .categoryId(parentCategoryId)
                    .name("부모 카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryDomainService.validateParentCategory(parentCategoryId)).thenReturn(parentCategory);
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
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("기존 카테고리")
                    .parentId(null)
                    .status(Category.STATUS_ACTIVE)
                    .build();

            UpdateCategoryResponse expectedResponse = new UpdateCategoryResponse(
                    "수정된 카테고리",
                    "Category '수정된 카테고리' updated successfully."
            );

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            when(categoryDomainService.isCategoryNameUniqueForUpdate("수정된 카테고리", categoryIdVo)).thenReturn(true);
            when(categoryRepository.saveAll(List.of(existingCategory))).thenReturn(List.of(existingCategory));
            when(categoryDataMapper.toUpdateCategoryResponse(eq(existingCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            UpdateCategoryResponse response = categoryApplicationService.updateCategory(categoryId, command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("수정된 카테고리");
            assertThat(response.message()).contains("updated successfully");

            verify(categoryDomainService).isCategoryNameUniqueForUpdate("수정된 카테고리", categoryIdVo);
            verify(categoryRepository).saveAll(List.of(existingCategory));
        }

        @Test
        @DisplayName("카테고리 부모 변경 성공")
        void updateCategory_ChangeParent_Success() {
            // Given
            Long categoryId = 1L;
            Long newParentId = 2L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            CategoryId newParentIdVo = new CategoryId(newParentId);

            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .parentId(newParentId)
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("카테고리")
                    .parentId(null)
                    .status(Category.STATUS_ACTIVE)
                    .build();

            UpdateCategoryResponse expectedResponse = new UpdateCategoryResponse(
                    "카테고리",
                    "Category '카테고리' updated successfully."
            );

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            doNothing().when(categoryDomainService).validateParentChangeRules(categoryIdVo, newParentIdVo);
            when(categoryRepository.saveAll(List.of(existingCategory))).thenReturn(List.of(existingCategory));
            when(categoryDataMapper.toUpdateCategoryResponse(eq(existingCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            UpdateCategoryResponse response = categoryApplicationService.updateCategory(categoryId, command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.message()).contains("updated successfully");

            verify(categoryDomainService).validateParentChangeRules(categoryIdVo, newParentIdVo);
            verify(categoryRepository).saveAll(List.of(existingCategory));
        }

        @Test
        @DisplayName("카테고리 상태 변경 성공 (ACTIVE -> INACTIVE)")
        void updateCategory_ChangeStatusToInactive_Success() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .status(Category.STATUS_INACTIVE)
                    .build();

            Category mainCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("메인 카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            Category childCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("자식 카테고리")
                    .parentId(categoryIdVo)
                    .status(Category.STATUS_ACTIVE)
                    .build();

            List<Category> categoriesToUpdate = List.of(mainCategory, childCategory);

            UpdateCategoryResponse expectedResponse = new UpdateCategoryResponse(
                    "메인 카테고리",
                    "Category '메인 카테고리' updated successfully."
            );

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(mainCategory));
            when(categoryRepository.findSubTreeByIdAndStatusIn(categoryIdVo, List.of(Category.STATUS_ACTIVE)))
                    .thenReturn(categoriesToUpdate);
            doNothing().when(categoryDomainService).validateStatusChangeRules(categoriesToUpdate, Category.STATUS_INACTIVE);
            when(categoryRepository.saveAll(categoriesToUpdate)).thenReturn(categoriesToUpdate);
            when(categoryDataMapper.toUpdateCategoryResponse(eq(mainCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            UpdateCategoryResponse response = categoryApplicationService.updateCategory(categoryId, command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.message()).contains("updated successfully");

            verify(categoryDomainService).validateStatusChangeRules(categoriesToUpdate, Category.STATUS_INACTIVE);
            verify(categoryRepository).saveAll(categoriesToUpdate);
        }

        @Test
        @DisplayName("변경사항이 없는 경우")
        void updateCategory_NoChanges_Success() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("기존 카테고리")  // 동일한 이름
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("기존 카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            UpdateCategoryResponse expectedResponse = new UpdateCategoryResponse(
                    "기존 카테고리",
                    "Category '기존 카테고리' was not changed."
            );

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            when(categoryDataMapper.toUpdateCategoryResponse(eq(existingCategory), anyString()))
                    .thenReturn(expectedResponse);

            // When
            UpdateCategoryResponse response = categoryApplicationService.updateCategory(categoryId, command);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.message()).contains("was not changed");

            verify(categoryRepository, never()).saveAll(anyList());
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

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id")
                    .hasMessageContaining("not found");

            verify(categoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("삭제된 카테고리 수정 실패")
        void updateCategory_DeletedCategory_ThrowsException() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("수정된 카테고리")
                    .build();

            Category deletedCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("삭제된 카테고리")
                    .status(Category.STATUS_DELETED)
                    .build();

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(deletedCategory));

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Cannot update a category that has been deleted");

            verify(categoryRepository, never()).saveAll(anyList());
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

        @Test
        @DisplayName("중복된 이름으로 수정 실패")
        void updateCategory_DuplicateName_ThrowsException() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("중복된 이름")
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("기존 카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            when(categoryDomainService.isCategoryNameUniqueForUpdate("중복된 이름", categoryIdVo)).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(DuplicateCategoryNameException.class)
                    .hasMessageContaining("already exists");

            verify(categoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("도메인 서비스에서 부모 변경 규칙 검증 실패")
        void updateCategory_ParentChangeValidationFailed_ThrowsException() {
            // Given
            Long categoryId = 1L;
            Long newParentId = 2L;
            CategoryId categoryIdVo = new CategoryId(categoryId);
            CategoryId newParentIdVo = new CategoryId(newParentId);

            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .parentId(newParentId)
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            doThrow(new CategoryDomainException("Circular reference detected"))
                    .when(categoryDomainService).validateParentChangeRules(categoryIdVo, newParentIdVo);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Circular reference detected");

            verify(categoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("도메인 서비스에서 상태 변경 규칙 검증 실패")
        void updateCategory_StatusChangeValidationFailed_ThrowsException() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .status(Category.STATUS_INACTIVE)  // ACTIVE -> INACTIVE 변경
                    .build();

            Category existingCategory = Category.builder()
                    .categoryId(categoryIdVo)
                    .name("카테고리")
                    .status(Category.STATUS_ACTIVE)
                    .build();

            Category childCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("자식 카테고리")
                    .parentId(categoryIdVo)
                    .status(Category.STATUS_ACTIVE)
                    .build();

            List<Category> categoriesToUpdate = List.of(existingCategory, childCategory);

            when(categoryRepository.findById(categoryIdVo)).thenReturn(java.util.Optional.of(existingCategory));
            when(categoryRepository.findSubTreeByIdAndStatusIn(categoryIdVo, List.of(Category.STATUS_ACTIVE)))
                    .thenReturn(categoriesToUpdate);
            doThrow(new CategoryDomainException("Invalid status transition"))
                    .when(categoryDomainService).validateStatusChangeRules(categoriesToUpdate, Category.STATUS_INACTIVE);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(categoryId, command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Invalid status transition");

            verify(categoryRepository, never()).saveAll(anyList());
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
                    .status(Category.STATUS_DELETED)  // 도메인 서비스에서 이미 삭제 표시됨
                    .build();

            Category childCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("자식 카테고리")
                    .parentId(categoryIdVo)
                    .status(Category.STATUS_DELETED)  // 도메인 서비스에서 이미 삭제 표시됨
                    .build();

            List<Category> categoriesToDelete = List.of(rootCategory, childCategory);

            when(categoryDomainService.prepareForDeletion(categoryIdVo)).thenReturn(categoriesToDelete);
            when(categoryRepository.saveAll(categoriesToDelete)).thenReturn(categoriesToDelete);

            // When
            DeleteCategoryResponse response = categoryApplicationService.deleteCategory(categoryId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(categoryId);
            assertThat(response.message()).contains("marked as deleted successfully");

            verify(categoryDomainService).prepareForDeletion(categoryIdVo);
            verify(categoryRepository).saveAll(categoriesToDelete);
        }

        @Test
        @DisplayName("존재하지 않는 카테고리 삭제 실패")
        void deleteCategory_CategoryNotFound_ThrowsException() {
            // Given
            Long categoryId = 999L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            when(categoryDomainService.prepareForDeletion(categoryIdVo))
                    .thenThrow(new CategoryNotFoundException("Category with id 999 not found."));

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

            verify(categoryDomainService, never()).prepareForDeletion(any());
        }

        @Test
        @DisplayName("도메인 서비스에서 삭제 규칙 검증 실패")
        void deleteCategory_DomainValidationFailed_ThrowsException() {
            // Given
            Long categoryId = 1L;
            CategoryId categoryIdVo = new CategoryId(categoryId);

            when(categoryDomainService.prepareForDeletion(categoryIdVo))
                    .thenThrow(new CategoryDomainException("Cannot delete category with active products"));

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.deleteCategory(categoryId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Cannot delete category with active products");

            verify(categoryRepository, never()).saveAll(anyList());
        }
    }
}