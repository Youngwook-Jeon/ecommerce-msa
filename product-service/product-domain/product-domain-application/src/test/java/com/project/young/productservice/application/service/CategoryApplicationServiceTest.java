package com.project.young.productservice.application.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.application.dto.CreateCategoryCommand;
import com.project.young.productservice.application.dto.CreateCategoryResult;
import com.project.young.productservice.application.dto.DeleteCategoryResult;
import com.project.young.productservice.application.dto.UpdateCategoryCommand;
import com.project.young.productservice.application.dto.UpdateCategoryResult;
import com.project.young.productservice.application.mapper.CategoryDataMapper;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.exception.DuplicateCategoryNameException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.service.CategoryDomainService;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
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
    @DisplayName("createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("부모 없는 카테고리 생성 성공")
        void createCategory_WithoutParent_Success() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("최상위")
                    .parentId(null)
                    .build();

            Category toSave = Category.builder()
                    .name("최상위")
                    .parentId(null)
                    .build();

            Category saved = Category.reconstitute(
                    new CategoryId(10L),
                    "최상위",
                    null,
                    CategoryStatus.ACTIVE
            );

            CreateCategoryResult expected = CreateCategoryResult.builder()
                    .id(10L)
                    .name("최상위")
                    .build();

            when(categoryDomainService.isCategoryNameUnique("최상위")).thenReturn(true);
            when(categoryDataMapper.toCategory(command, null)).thenReturn(toSave);
            when(categoryRepository.save(toSave)).thenReturn(saved);
            when(categoryDataMapper.toCreateCategoryResult(saved)).thenReturn(expected);

            // When
            CreateCategoryResult result = categoryApplicationService.createCategory(command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(10L);
            assertThat(result.name()).isEqualTo("최상위");

            verify(categoryDomainService).isCategoryNameUnique("최상위");
            verify(categoryDomainService, never()).validateParentCategory(any());
            verify(categoryRepository).save(toSave);
            verify(categoryDataMapper).toCreateCategoryResult(saved);
        }

        @Test
        @DisplayName("부모 있는 카테고리 생성 성공")
        void createCategory_WithParent_Success() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("노트북")
                    .parentId(1L)
                    .build();

            CategoryId parentId = new CategoryId(1L);
            Category parentCategory = Category.reconstitute(
                    parentId,
                    "전자제품",
                    null,
                    CategoryStatus.ACTIVE
            );

            Category toSave = Category.builder()
                    .name("노트북")
                    .parentId(parentId)
                    .build();

            Category saved = Category.reconstitute(
                    new CategoryId(11L),
                    "노트북",
                    parentId,
                    CategoryStatus.ACTIVE
            );

            CreateCategoryResult expected = CreateCategoryResult.builder()
                    .id(11L)
                    .name("노트북")
                    .build();

            when(categoryDomainService.isCategoryNameUnique("노트북")).thenReturn(true);
            when(categoryDomainService.validateParentCategory(parentId)).thenReturn(parentCategory);
            when(categoryDomainService.isParentDepthLessThanLimit(parentCategory.getId())).thenReturn(true);

            when(categoryDataMapper.toCategory(command, parentId)).thenReturn(toSave);
            when(categoryRepository.save(toSave)).thenReturn(saved);
            when(categoryDataMapper.toCreateCategoryResult(saved)).thenReturn(expected);

            // When
            CreateCategoryResult result = categoryApplicationService.createCategory(command);

            // Then
            assertThat(result.id()).isEqualTo(11L);
            assertThat(result.name()).isEqualTo("노트북");

            verify(categoryDomainService).validateParentCategory(parentId);
            verify(categoryDomainService).isParentDepthLessThanLimit(parentCategory.getId());
            verify(categoryRepository).save(toSave);
        }

        @Test
        @DisplayName("이름 중복이면 DuplicateCategoryNameException")
        void createCategory_DuplicateName_ThrowsException() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("중복")
                    .parentId(null)
                    .build();

            when(categoryDomainService.isCategoryNameUnique("중복")).thenReturn(false);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(DuplicateCategoryNameException.class)
                    .hasMessageContaining("already exists");

            verify(categoryRepository, never()).save(any());
            verify(categoryDataMapper, never()).toCreateCategoryResult(any());
        }

        @Test
        @DisplayName("저장 후 ID가 할당되지 않으면 CategoryDomainException")
        void createCategory_IdNotAssigned_ThrowsException() {
            // Given
            CreateCategoryCommand command = CreateCategoryCommand.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build();

            Category toSave = Category.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build();

            Category savedWithoutId = Category.builder()
                    .name("새 카테고리")
                    .parentId(null)
                    .build();

            when(categoryDomainService.isCategoryNameUnique("새 카테고리")).thenReturn(true);
            when(categoryDataMapper.toCategory(command, null)).thenReturn(toSave);
            when(categoryRepository.save(toSave)).thenReturn(savedWithoutId);

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.createCategory(command))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Failed to assign ID");

            verify(categoryDataMapper, never()).toCreateCategoryResult(any());
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategoryTests {

        @Test
        @DisplayName("요청이 null이면 IllegalArgumentException")
        void updateCategory_InvalidRequest_ThrowsException() {
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid update request");
        }

        @Test
        @DisplayName("대상이 없으면 CategoryNotFoundException")
        void updateCategory_NotFound_ThrowsException() {
            // Given
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("변경")
                    .status(CategoryStatus.ACTIVE)
                    .build();

            when(categoryRepository.findById(new CategoryId(999L))).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryApplicationService.updateCategory(999L, command))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id 999 not found");

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("이름 변경 시 저장하고 UpdateCategoryResult 반환")
        void updateCategory_ChangeName_SavesAndReturnsResult() {
            // Given
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name("수정된 이름")
                    .status(CategoryStatus.ACTIVE)
                    .build();

            CategoryId id = new CategoryId(1L);
            Category main = Category.reconstitute(id, "기존 이름", null, CategoryStatus.ACTIVE);

            UpdateCategoryResult expected = UpdateCategoryResult.builder()
                    .id(1L)
                    .name("수정된 이름")
                    .parentId(null)
                    .status(CategoryStatus.ACTIVE)
                    .build();

            when(categoryRepository.findById(id)).thenReturn(Optional.of(main));
            when(categoryDomainService.isCategoryNameUniqueForUpdate("수정된 이름", id)).thenReturn(true);
            when(categoryRepository.save(main)).thenReturn(main);
            when(categoryDataMapper.toUpdateCategoryResult(main)).thenReturn(expected);

            // When
            UpdateCategoryResult result = categoryApplicationService.updateCategory(1L, command);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("수정된 이름");

            verify(categoryDomainService).isCategoryNameUniqueForUpdate("수정된 이름", id);
            verify(categoryRepository).save(main);
            verify(categoryDataMapper).toUpdateCategoryResult(main);
        }

        @Test
        @DisplayName("상태 변경 시 bulk update 수행 후 저장한다")
        void updateCategory_StatusChange_PerformsBulkUpdate() {
            // Given
            UpdateCategoryCommand command = UpdateCategoryCommand.builder()
                    .name(null)
                    .parentId(null)
                    .status(CategoryStatus.INACTIVE)
                    .build();

            CategoryId id = new CategoryId(1L);
            Category main = Category.reconstitute(id, "카테고리", null, CategoryStatus.ACTIVE);

            List<CategoryId> affectedIds = List.of(id, new CategoryId(2L));
            List<Category> affectedCategories = List.of(
                    Category.reconstitute(new CategoryId(1L), "카테고리", null, CategoryStatus.ACTIVE),
                    Category.reconstitute(new CategoryId(2L), "자식", id, CategoryStatus.ACTIVE)
            );

            UpdateCategoryResult expected = UpdateCategoryResult.builder()
                    .id(1L)
                    .name("카테고리")
                    .parentId(null)
                    .status(CategoryStatus.INACTIVE)
                    .build();

            when(categoryRepository.findById(id)).thenReturn(Optional.of(main));
            when(categoryDomainService.getAffectedCategories(id, CategoryStatus.INACTIVE)).thenReturn(affectedIds);
            when(categoryRepository.findAllById(affectedIds)).thenReturn(affectedCategories);
            doNothing().when(categoryDomainService).validateStatusChangeRules(affectedCategories, CategoryStatus.INACTIVE);
            doNothing().when(categoryRepository).updateStatusForIds(CategoryStatus.INACTIVE, affectedIds);

            when(categoryRepository.save(main)).thenReturn(main);
            when(categoryDataMapper.toUpdateCategoryResult(main)).thenReturn(expected);

            // When
            UpdateCategoryResult result = categoryApplicationService.updateCategory(1L, command);

            // Then
            assertThat(result.status()).isEqualTo(CategoryStatus.INACTIVE);

            verify(categoryRepository).findAllById(affectedIds);
            verify(categoryDomainService).validateStatusChangeRules(affectedCategories, CategoryStatus.INACTIVE);
            verify(categoryRepository).updateStatusForIds(CategoryStatus.INACTIVE, affectedIds);
            verify(categoryRepository).save(main);
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategoryTests {

        @Test
        @DisplayName("null id이면 IllegalArgumentException")
        void deleteCategory_NullId_ThrowsException() {
            assertThatThrownBy(() -> categoryApplicationService.deleteCategory(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Category ID for delete cannot be null");

            verify(categoryDomainService, never()).prepareForDeletion(any());
            verify(categoryRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("삭제 성공 시 root 정보를 반환한다")
        void deleteCategory_Success() {
            // Given
            CategoryId rootId = new CategoryId(1L);

            Category rootDeleted = Category.reconstitute(rootId, "루트", null, CategoryStatus.DELETED);
            Category childDeleted = Category.reconstitute(new CategoryId(2L), "자식", rootId, CategoryStatus.DELETED);

            List<Category> toDelete = List.of(rootDeleted, childDeleted);

            when(categoryDomainService.prepareForDeletion(rootId)).thenReturn(toDelete);
            when(categoryRepository.saveAll(toDelete)).thenReturn(toDelete);

            // When
            DeleteCategoryResult result = categoryApplicationService.deleteCategory(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("루트");

            verify(categoryDomainService).prepareForDeletion(rootId);
            verify(categoryRepository).saveAll(toDelete);
        }
    }
}