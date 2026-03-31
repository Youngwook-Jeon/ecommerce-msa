package com.project.young.productservice.domain.service;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryDomainException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        @DisplayName("parent가 null이면 예외")
        void validateParentCategory_NullParent_Throws() {
            assertThatThrownBy(() -> categoryDomainService.validateParentCategory(null))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Parent category must not be null");
        }

        @Test
        @DisplayName("부모가 ACTIVE가 아니면 예외")
        void validateParentCategory_NotActive_Throws() {
            CategoryId parentId = new CategoryId(1L);
            Category parent = Category.reconstitute(parentId, "부모", null, CategoryStatus.INACTIVE);

            assertThatThrownBy(() -> categoryDomainService.validateParentCategory(parent))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("only be created/moved under an 'ACTIVE' parent");
        }

        @Test
        @DisplayName("부모가 ACTIVE면 통과")
        void validateParentCategory_Active_Allows() {
            CategoryId parentId = new CategoryId(1L);
            Category parent = Category.reconstitute(parentId, "부모", null, CategoryStatus.ACTIVE);
            assertThatCode(() -> categoryDomainService.validateParentCategory(parent))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("validateParentChangeRules")
    class ValidateParentChangeRulesTests {

        @Test
        @DisplayName("newParentId가 null이면 항상 통과")
        void validateParentChangeRules_NewParentNull_Allows() {
            CategoryId categoryId = new CategoryId(1L);

            categoryDomainService.validateParentChangeRules(categoryId, null, null);

            verifyNoInteractions(categoryRepository);
        }

        @Test
        @DisplayName("새 부모 엔티티가 null이면 예외")
        void validateParentChangeRules_NewParentEntityNull_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(2L);

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId, null))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Parent category must not be null");

            verify(categoryRepository, never()).findSubTreeByIdAndStatusIn(any(), anyList());
            verify(categoryRepository, never()).getDepth(any());
            verify(categoryRepository, never()).getMaxSubtreeDepthByIdAndStatusIn(any(), anyList());
        }

        @Test
        @DisplayName("새 부모가 ACTIVE가 아니면 예외")
        void validateParentChangeRules_NewParentNotActive_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(2L);

            Category inactiveParent = Category.reconstitute(newParentId, "부모", null, CategoryStatus.INACTIVE);

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId, inactiveParent))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("only be created/moved under an 'ACTIVE' parent");

            verify(categoryRepository, never()).findSubTreeByIdAndStatusIn(any(), anyList());
            verify(categoryRepository, never()).getDepth(any());
            verify(categoryRepository, never()).getMaxSubtreeDepthByIdAndStatusIn(any(), anyList());
        }

        @Test
        @DisplayName("자기 자신을 부모로 설정하면 예외 (부모 ACTIVE 검증은 먼저 수행됨)")
        void validateParentChangeRules_SelfParent_Throws() {
            CategoryId categoryId = new CategoryId(1L);

            Category activeSelfAsParent = Category.reconstitute(categoryId, "self", null, CategoryStatus.ACTIVE);

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, categoryId, activeSelfAsParent))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("own parent");

            verify(categoryRepository, never()).findSubTreeByIdAndStatusIn(any(), anyList());
            verify(categoryRepository, never()).getDepth(any());
            verify(categoryRepository, never()).getMaxSubtreeDepthByIdAndStatusIn(any(), anyList());
        }

        @Test
        @DisplayName("순환 참조면 예외")
        void validateParentChangeRules_CircularReference_Throws() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId newParentId = new CategoryId(3L);

            Category activeParent = Category.reconstitute(newParentId, "parent", null, CategoryStatus.ACTIVE);

            Category descendant = Category.reconstitute(newParentId, "desc", categoryId, CategoryStatus.ACTIVE);
            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(List.of(descendant));

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId, activeParent))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("Circular reference detected");

            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(categoryId), anyList());
            verify(categoryRepository, never()).getDepth(any());
            verify(categoryRepository, never()).getMaxSubtreeDepthByIdAndStatusIn(any(), anyList());
        }

        @Test
        @DisplayName("깊이 제한을 넘으면 예외 (parentDepth + subtreeHeight)")
        void validateParentChangeRules_DepthLimitExceeded_Throws() {
            CategoryId categoryId = new CategoryId(10L);
            CategoryId newParentId = new CategoryId(20L);

            Category activeParent = Category.reconstitute(newParentId, "parent", null, CategoryStatus.ACTIVE);

            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(Collections.emptyList());

            when(categoryRepository.getDepth(newParentId)).thenReturn(4);
            when(categoryRepository.getMaxSubtreeDepthByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(2); // 4 + 2 = 6 > MAX(5)

            assertThatThrownBy(() -> categoryDomainService.validateParentChangeRules(categoryId, newParentId, activeParent))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("depth limit exceeded");

            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(categoryId), anyList());
            verify(categoryRepository).getDepth(newParentId);
            verify(categoryRepository).getMaxSubtreeDepthByIdAndStatusIn(eq(categoryId), anyList());
        }

        @Test
        @DisplayName("정상 케이스면 통과")
        void validateParentChangeRules_Success() {
            CategoryId categoryId = new CategoryId(10L);
            CategoryId newParentId = new CategoryId(20L);

            Category activeParent = Category.reconstitute(newParentId, "parent", null, CategoryStatus.ACTIVE);

            when(categoryRepository.findSubTreeByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(Collections.emptyList());

            when(categoryRepository.getDepth(newParentId)).thenReturn(0);
            when(categoryRepository.getMaxSubtreeDepthByIdAndStatusIn(eq(categoryId), anyList()))
                    .thenReturn(1); // 0 + 1 = 1 <= MAX(5)

            categoryDomainService.validateParentChangeRules(categoryId, newParentId, activeParent);

            verify(categoryRepository).findSubTreeByIdAndStatusIn(eq(categoryId), anyList());
            verify(categoryRepository).getDepth(newParentId);
            verify(categoryRepository).getMaxSubtreeDepthByIdAndStatusIn(eq(categoryId), anyList());
        }
    }

    @Nested
    @DisplayName("validateStatusChangeRules")
    class ValidateStatusChangeRulesTests {

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
    @DisplayName("validateDeletionRules")
    class ValidateDeletionRulesTests {

        @Test
        @DisplayName("대상이 비어 있으면 CategoryDomainException")
        void validateDeletionRules_Empty_Throws() {
            assertThatThrownBy(() -> categoryDomainService.validateDeletionRules(Collections.emptyList()))
                    .isInstanceOf(CategoryDomainException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("대상이 있으면 검증만 수행하고 상태 변경은 하지 않음")
        void validateDeletionRules_WithCategories_DoesNotMutate() {
            CategoryId id = new CategoryId(1L);

            Category root = Category.reconstitute(new CategoryId(1L), "root", null, CategoryStatus.ACTIVE);
            Category child = Category.reconstitute(new CategoryId(2L), "child", id, CategoryStatus.INACTIVE);

            categoryDomainService.validateDeletionRules(List.of(root, child));

            assertThat(root.getStatus()).isEqualTo(CategoryStatus.ACTIVE);
            assertThat(child.getStatus()).isEqualTo(CategoryStatus.INACTIVE);
        }
    }

}