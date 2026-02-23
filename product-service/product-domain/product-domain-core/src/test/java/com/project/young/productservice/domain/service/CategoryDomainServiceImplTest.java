package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.repository.CategoryRepository;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryDomainServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryDomainServiceImpl categoryDomainService;

    @Nested
    @DisplayName("validateParentCategory")
    class ValidateParentCategoryTests {

        @Test
        @DisplayName("parentId가 null이면 null 반환")
        void validateParentCategory_NullParent_ReturnsNull() {
            Category result = categoryDomainService.validateParentCategory(null);
            assertThat(result).isNull();
            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("부모가 없으면 예외")
        void validateParentCategory_NotFound_Throws() {
            CategoryId parentId = new CategoryId(1L);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryDomainService.validateParentCategory(parentId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Parent category with id 1 not found");

            verify(categoryRepository).findById(parentId);
        }

        @Test
        @DisplayName("부모가 ACTIVE가 아니면 예외")
        void validateParentCategory_NotActive_Throws() {
            CategoryId parentId = new CategoryId(1L);
            Category parent = Category.reconstitute(parentId, "부모", null, CategoryStatus.INACTIVE);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));

            assertThatThrownBy(() -> categoryDomainService.validateParentCategory(parentId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("only be created under an 'ACTIVE' parent");

            verify(categoryRepository).findById(parentId);
        }

        @Test
        @DisplayName("부모가 ACTIVE면 부모 반환")
        void validateParentCategory_Active_ReturnsParent() {
            CategoryId parentId = new CategoryId(1L);
            Category parent = Category.reconstitute(parentId, "부모", null, CategoryStatus.ACTIVE);
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));

            Category result = categoryDomainService.validateParentCategory(parentId);

            assertThat(result).isSameAs(parent);
            verify(categoryRepository).findById(parentId);
        }
    }

    @Nested
    @DisplayName("validateParentChangeRules")
    class ValidateParentChangeRulesTests {

        @Test
        @DisplayName("newParentId가 null이면 항상 통과")
        void validateParentChangeRules_NewParentNull_Allows() {
            CategoryId categoryId = new CategoryId(1L);

            categoryDomainService.validateParentChangeRules(categoryId, null);

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("새 부모가 존재하지 않으면 예외")
        void validateParentChangeRules_NewParentNotFound_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(2L);

            when(categoryRepository.existsById(newParentId)).thenReturn(false);

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("New parent category with id 2 not found");

            verify(categoryRepository).existsById(newParentId);
        }

        @Test
        @DisplayName("자기 자신을 부모로 설정하면 예외")
        void validateParentChangeRules_SelfParent_Throws() {
            CategoryId categoryId = new CategoryId(1L);

            when(categoryRepository.existsById(categoryId)).thenReturn(true);

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, categoryId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("own parent");

            verify(categoryRepository).existsById(categoryId);
        }

        @Test
        @DisplayName("순환 참조면 예외")
        void validateParentChangeRules_CircularReference_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(3L);

            when(categoryRepository.existsById(newParentId)).thenReturn(true);

            Category descendant = Category.reconstitute(newParentId, "desc", categoryId, CategoryStatus.ACTIVE);
            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(List.of(descendant));

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Circular reference detected");

            verify(categoryRepository).existsById(newParentId);
            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(categoryId), anyList());
        }

        @Test
        @DisplayName("깊이 제한을 넘으면 예외")
        void validateParentChangeRules_DepthLimitExceeded_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(2L);

            when(categoryRepository.existsById(newParentId)).thenReturn(true);
            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.getDepth(newParentId)).thenReturn(5); // parentDepth < 5 이어야 통과

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("depth limit exceeded");

            verify(categoryRepository).getDepth(newParentId);
        }

        @Test
        @DisplayName("정상 케이스면 통과")
        void validateParentChangeRules_Success() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(2L);

            when(categoryRepository.existsById(newParentId)).thenReturn(true);
            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(Collections.emptyList());
            when(categoryRepository.getDepth(newParentId)).thenReturn(0);

            categoryDomainService.validateParentChangeRules(categoryId, newParentId);

            verify(categoryRepository).existsById(newParentId);
            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(categoryId), anyList());
            verify(categoryRepository).getDepth(newParentId);
        }
    }

    @Nested
    @DisplayName("validateStatusChangeRules")
    class ValidateStatusChangeRulesTests {

        @Test
        @DisplayName("newStatus가 null이면 아무것도 하지 않음")
        void validateStatusChangeRules_NullNewStatus_NoOp() {
            categoryDomainService.validateStatusChangeRules(List.of(
                    Category.reconstitute(new CategoryId(1L), "A", null, CategoryStatus.ACTIVE)
            ), null);
        }

        @Test
        @DisplayName("삭제된 카테고리가 포함되면 예외")
        void validateStatusChangeRules_DeletedCategory_Throws() {
            List<Category> categories = List.of(
                    Category.reconstitute(new CategoryId(1L), "A", null, CategoryStatus.DELETED)
            );

            assertThatThrownBy(() -> categoryDomainService.validateStatusChangeRules(categories, CategoryStatus.ACTIVE))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("deleted category");
        }

        @Test
        @DisplayName("삭제되지 않은 카테고리: ACTIVE -> DELETED 전이는 허용된다")
        void validateStatusChangeRules_ActiveToDeleted_Allows() {
            Category category = Category.reconstitute(new CategoryId(1L), "A", null, CategoryStatus.ACTIVE);

            categoryDomainService.validateStatusChangeRules(List.of(category), CategoryStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("prepareForDeletion")
    class PrepareForDeletionTests {

        @Test
        @DisplayName("대상이 없으면 CategoryNotFoundException")
        void prepareForDeletion_NotFound_Throws() {
            CategoryId id = new CategoryId(1L);
            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(id), anyList()))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> categoryDomainService.prepareForDeletion(id))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id 1 not found");

            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(id), anyList());
        }

        @Test
        @DisplayName("대상이 있으면 모두 DELETED로 마킹")
        void prepareForDeletion_MarksAllDeleted() {
            CategoryId id = new CategoryId(1L);

            Category root = Category.reconstitute(new CategoryId(1L), "root", null, CategoryStatus.ACTIVE);
            Category child = Category.reconstitute(new CategoryId(2L), "child", id, CategoryStatus.INACTIVE);

            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(id), anyList()))
                    .thenReturn(List.of(root, child));

            List<Category> result = categoryDomainService.prepareForDeletion(id);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(c -> c.getStatus() == CategoryStatus.DELETED);
        }
    }

    @Nested
    @DisplayName("getAffectedCategories")
    class GetAffectedCategoriesTests {

        @Test
        @DisplayName("INACTIVE -> ACTIVE: 조상들을 반환")
        void getAffectedCategories_ToActive_ReturnsAncestors() {
            CategoryId id = new CategoryId(10L);

            Category a = Category.reconstitute(new CategoryId(1L), "a", null, CategoryStatus.INACTIVE);
            Category b = Category.reconstitute(id, "b", new CategoryId(1L), CategoryStatus.INACTIVE);

            when(categoryRepository.findAllAncestorsById(id)).thenReturn(List.of(a, b));

            List<CategoryId> result = categoryDomainService.getAffectedCategories(id, CategoryStatus.ACTIVE);

            assertThat(result).containsExactly(new CategoryId(1L), id);
            verify(categoryRepository).findAllAncestorsById(id);
        }

        @Test
        @DisplayName("ACTIVE -> INACTIVE: ACTIVE 상태의 서브트리를 반환")
        void getAffectedCategories_ToInactive_ReturnsActiveSubtree() {
            CategoryId id = new CategoryId(1L);

            Category root = Category.reconstitute(id, "root", null, CategoryStatus.ACTIVE);
            Category child = Category.reconstitute(new CategoryId(2L), "child", id, CategoryStatus.ACTIVE);

            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(id), anyList()))
                    .thenReturn(List.of(root, child));

            List<CategoryId> result = categoryDomainService.getAffectedCategories(id, CategoryStatus.INACTIVE);

            assertThat(result).containsExactly(id, new CategoryId(2L));
            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(id), anyList());
        }

        @Test
        @DisplayName("DELETED는 허용하지 않음")
        void getAffectedCategories_Deleted_Throws() {
            CategoryId id = new CategoryId(1L);

            assertThatThrownBy(() -> categoryDomainService.getAffectedCategories(id, CategoryStatus.DELETED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status");
        }
    }
}