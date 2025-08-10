package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import jakarta.persistence.EntityNotFoundException;
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
class CategoryRepositoryImplTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    @Mock
    private CategoryDataAccessMapper categoryDataAccessMapper;

    @InjectMocks
    private CategoryRepositoryImpl categoryRepository;

    @Nested
    @DisplayName("카테고리 저장 테스트")
    class SaveCategoryTests {

        @Test
        @DisplayName("부모가 없는 새 카테고리 저장 성공")
        void save_NewCategoryWithoutParent_Success() {
            // Given
            Category category = Category.builder()
                    .name("전자제품")
                    .status("ACTIVE")
                    .parentId(null)
                    .build(); // ID가 null인 새 카테고리

            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    new CategoryId(1L),
                    "전자제품",
                    null,
                    "ACTIVE"
            );

            when(categoryDataAccessMapper.categoryToCategoryEntity(category, null))
                    .thenReturn(categoryEntity);
            when(categoryJpaRepository.save(categoryEntity)).thenReturn(savedCategoryEntity);
            when(categoryDataAccessMapper.categoryEntityToCategory(savedCategoryEntity))
                    .thenReturn(savedCategory);

            // When
            Category result = categoryRepository.save(category);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("전자제품");
            assertThat(result.getId().getValue()).isEqualTo(1L);

            verify(categoryDataAccessMapper).categoryToCategoryEntity(category, null);
            verify(categoryJpaRepository).save(categoryEntity);
            verify(categoryDataAccessMapper).categoryEntityToCategory(savedCategoryEntity);
        }

        @Test
        @DisplayName("부모가 있는 새 카테고리 저장 성공")
        void save_NewCategoryWithParent_Success() {
            // Given
            CategoryId parentId = new CategoryId(1L);
            Category category = Category.builder()
                    .name("노트북")
                    .status("ACTIVE")
                    .parentId(parentId)
                    .build(); // ID가 null인 새 카테고리

            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    new CategoryId(2L),
                    "노트북",
                    parentId,
                    "ACTIVE"
            );

            when(categoryJpaRepository.getReferenceById(1L)).thenReturn(parentRefEntity);
            when(categoryDataAccessMapper.categoryToCategoryEntity(category, parentRefEntity))
                    .thenReturn(categoryEntity);
            when(categoryJpaRepository.save(categoryEntity)).thenReturn(savedCategoryEntity);
            when(categoryDataAccessMapper.categoryEntityToCategory(savedCategoryEntity))
                    .thenReturn(savedCategory);

            // When
            Category result = categoryRepository.save(category);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("노트북");
            assertThat(result.getId().getValue()).isEqualTo(2L);

            verify(categoryJpaRepository).getReferenceById(1L);
            verify(categoryDataAccessMapper).categoryToCategoryEntity(category, parentRefEntity);
            verify(categoryJpaRepository).save(categoryEntity);
        }

        @Test
        @DisplayName("기존 카테고리 업데이트 성공")
        void save_UpdateExistingCategory_Success() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            CategoryId parentId = new CategoryId(2L);
            Category category = Category.reconstitute(
                    categoryId,
                    "수정된 카테고리",
                    parentId,
                    "INACTIVE"
            );

            CategoryEntity existingEntity = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity savedEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    categoryId,
                    "수정된 카테고리",
                    parentId,
                    "INACTIVE"
            );

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(categoryJpaRepository.getReferenceById(2L)).thenReturn(parentRefEntity);
            when(categoryJpaRepository.save(existingEntity)).thenReturn(savedEntity);
            when(categoryDataAccessMapper.categoryEntityToCategory(savedEntity)).thenReturn(savedCategory);

            // When
            Category result = categoryRepository.save(category);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("수정된 카테고리");

            verify(categoryJpaRepository).findById(1L);
            verify(categoryJpaRepository).getReferenceById(2L);
            verify(existingEntity).setName("수정된 카테고리");
            verify(existingEntity).setStatus("INACTIVE");
            verify(existingEntity).setParent(parentRefEntity);
            verify(categoryJpaRepository).save(existingEntity);
        }

        @Test
        @DisplayName("존재하지 않는 기존 카테고리 업데이트 시 예외 발생")
        void save_UpdateNonExistentCategory_ThrowsException() {
            // Given
            CategoryId categoryId = new CategoryId(999L);
            Category category = Category.reconstitute(
                    categoryId,
                    "존재하지 않는 카테고리",
                    null,
                    "ACTIVE"
            );

            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryRepository.save(category))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Category not found: 999");

            verify(categoryJpaRepository).findById(999L);
            verify(categoryJpaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("카테고리 일괄 저장 테스트")
    class SaveAllCategoriesTests {


        @Test
        @DisplayName("새 카테고리들 일괄 저장 성공")
        void saveAll_NewCategories_Success() {
            // Given
            Category category1 = Category.builder()
                    .name("전자제품")
                    .status("ACTIVE")
                    .build(); // ID가 null인 새 카테고리

            Category category2 = Category.builder()
                    .name("도서")
                    .status("ACTIVE")
                    .parentId(new CategoryId(1L))
                    .build(); // ID가 null인 새 카테고리

            List<Category> categories = List.of(category1, category2);

            CategoryEntity entity1 = mock(CategoryEntity.class);
            CategoryEntity entity2 = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity savedEntity1 = mock(CategoryEntity.class);
            CategoryEntity savedEntity2 = mock(CategoryEntity.class);

            Category savedCategory1 = Category.reconstitute(
                    new CategoryId(10L),
                    "전자제품",
                    null,
                    "ACTIVE"
            );

            Category savedCategory2 = Category.reconstitute(
                    new CategoryId(11L),
                    "도서",
                    new CategoryId(1L),
                    "ACTIVE"
            );

            when(categoryJpaRepository.findAllById(Collections.emptyList()))
                    .thenReturn(Collections.emptyList());

            // any() matcher를 사용하여 더 유연하게 mock 설정
            when(categoryDataAccessMapper.categoryToCategoryEntity(any(Category.class), eq(null)))
                    .thenReturn(entity1, entity2); // 첫 번째 호출은 entity1, 두 번째 호출은 entity2

            when(categoryJpaRepository.getReferenceById(1L)).thenReturn(parentRefEntity);
            when(categoryJpaRepository.saveAll(anyList()))
                    .thenReturn(List.of(savedEntity1, savedEntity2));
            when(categoryDataAccessMapper.categoryEntityToCategory(savedEntity1))
                    .thenReturn(savedCategory1);
            when(categoryDataAccessMapper.categoryEntityToCategory(savedEntity2))
                    .thenReturn(savedCategory2);

            // When
            List<Category> result = categoryRepository.saveAll(categories);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "도서");

            verify(categoryJpaRepository).findAllById(Collections.emptyList());
            // 전체적으로 2번 호출되었는지만 검증
            verify(categoryDataAccessMapper, times(2)).categoryToCategoryEntity(any(Category.class), eq(null));
            verify(categoryJpaRepository).getReferenceById(1L);
            verify(entity2).setParent(parentRefEntity);
            verify(categoryJpaRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("기존 카테고리들 일괄 업데이트 성공")
        void saveAll_UpdateExistingCategories_Success() {
            // Given
            CategoryId categoryId1 = new CategoryId(1L);
            CategoryId categoryId2 = new CategoryId(2L);

            Category category1 = Category.reconstitute(
                    categoryId1,
                    "수정된 전자제품",
                    null,
                    "INACTIVE"
            );

            Category category2 = Category.reconstitute(
                    categoryId2,
                    "수정된 도서",
                    new CategoryId(3L),
                    "ACTIVE"
            );

            List<Category> categories = List.of(category1, category2);

            CategoryEntity existingEntity1 = mock(CategoryEntity.class);
            CategoryEntity existingEntity2 = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);

            Category savedCategory1 = Category.reconstitute(
                    categoryId1,
                    "수정된 전자제품",
                    null,
                    "INACTIVE"
            );

            Category savedCategory2 = Category.reconstitute(
                    categoryId2,
                    "수정된 도서",
                    new CategoryId(3L),
                    "ACTIVE"
            );

            when(categoryJpaRepository.findAllById(List.of(1L, 2L)))
                    .thenReturn(List.of(existingEntity1, existingEntity2));
            when(existingEntity1.getId()).thenReturn(1L);
            when(existingEntity2.getId()).thenReturn(2L);
            when(categoryJpaRepository.getReferenceById(3L)).thenReturn(parentRefEntity);
            when(categoryJpaRepository.saveAll(anyList()))
                    .thenReturn(List.of(existingEntity1, existingEntity2));
            when(categoryDataAccessMapper.categoryEntityToCategory(existingEntity1))
                    .thenReturn(savedCategory1);
            when(categoryDataAccessMapper.categoryEntityToCategory(existingEntity2))
                    .thenReturn(savedCategory2);

            // When
            List<Category> result = categoryRepository.saveAll(categories);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("수정된 전자제품", "수정된 도서");

            verify(categoryJpaRepository).findAllById(List.of(1L, 2L));
            verify(categoryDataAccessMapper).updateEntityFromDomain(category1, existingEntity1);
            verify(categoryDataAccessMapper).updateEntityFromDomain(category2, existingEntity2);
            verify(categoryJpaRepository).getReferenceById(3L);
            verify(existingEntity2).setParent(parentRefEntity);
            verify(categoryJpaRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("혼합된 카테고리들(신규+기존) 일괄 저장 성공")
        void saveAll_MixedCategories_Success() {
            // Given
            CategoryId existingId = new CategoryId(1L);

            Category newCategory = Category.builder()
                    .name("새 카테고리")
                    .status("ACTIVE")
                    .build(); // ID가 null

            Category existingCategory = Category.reconstitute(
                    existingId,
                    "업데이트된 기존 카테고리",
                    null,
                    "INACTIVE"
            );

            List<Category> categories = List.of(newCategory, existingCategory);

            CategoryEntity newEntity = mock(CategoryEntity.class);
            CategoryEntity existingEntity = mock(CategoryEntity.class);

            Category savedNewCategory = Category.reconstitute(
                    new CategoryId(10L),
                    "새 카테고리",
                    null,
                    "ACTIVE"
            );

            Category savedExistingCategory = Category.reconstitute(
                    existingId,
                    "업데이트된 기존 카테고리",
                    null,
                    "INACTIVE"
            );

            when(categoryJpaRepository.findAllById(List.of(1L)))
                    .thenReturn(List.of(existingEntity));
            when(existingEntity.getId()).thenReturn(1L);
            when(categoryDataAccessMapper.categoryToCategoryEntity(newCategory, null))
                    .thenReturn(newEntity);
            when(categoryJpaRepository.saveAll(anyList()))
                    .thenReturn(List.of(newEntity, existingEntity));
            when(categoryDataAccessMapper.categoryEntityToCategory(newEntity))
                    .thenReturn(savedNewCategory);
            when(categoryDataAccessMapper.categoryEntityToCategory(existingEntity))
                    .thenReturn(savedExistingCategory);

            // When
            List<Category> result = categoryRepository.saveAll(categories);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("새 카테고리", "업데이트된 기존 카테고리");

            verify(categoryJpaRepository).findAllById(List.of(1L));
            verify(categoryDataAccessMapper).categoryToCategoryEntity(newCategory, null);
            verify(categoryDataAccessMapper).updateEntityFromDomain(existingCategory, existingEntity);
            verify(categoryJpaRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("존재하지 않는 기존 카테고리가 포함된 경우 예외 발생")
        void saveAll_WithNonExistentExistingCategory_ThrowsException() {
            // Given
            Category category = Category.reconstitute(
                    new CategoryId(999L),
                    "존재하지 않는 카테고리",
                    null,
                    "ACTIVE"
            );

            List<Category> categories = List.of(category);

            when(categoryJpaRepository.findAllById(List.of(999L)))
                    .thenReturn(Collections.emptyList()); // 빈 목록 반환

            // When & Then
            assertThatThrownBy(() -> categoryRepository.saveAll(categories))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Category with id 999 not found for update in saveAll");

            verify(categoryJpaRepository).findAllById(List.of(999L));
            verify(categoryJpaRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("null 목록으로 일괄 저장 시 빈 목록 반환")
        void saveAll_WithNullList_ReturnsEmptyList() {
            // When
            List<Category> result = categoryRepository.saveAll(null);

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("빈 목록으로 일괄 저장 시 빈 목록 반환")
        void saveAll_WithEmptyList_ReturnsEmptyList() {
            // When
            List<Category> result = categoryRepository.saveAll(Collections.emptyList());

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }
    }

    @Nested
    @DisplayName("존재 여부 확인 테스트")
    class ExistenceCheckTests {

        @Test
        @DisplayName("이름으로 카테고리 존재 여부 확인")
        void existsByName_Success() {
            // Given
            when(categoryJpaRepository.existsByName("전자제품")).thenReturn(true);

            // When
            boolean result = categoryRepository.existsByName("전자제품");

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("ID로 카테고리 존재 여부 확인")
        void existsById_Success() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            when(categoryJpaRepository.existsById(1L)).thenReturn(true);

            // When
            boolean result = categoryRepository.existsById(categoryId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("null CategoryId로 존재 여부 확인 시 예외 발생")
        void existsById_WithNullId_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> categoryRepository.existsById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CategoryId object can not be null");
        }

        @Test
        @DisplayName("특정 ID 제외하고 이름으로 존재 여부 확인")
        void existsByNameAndIdNot_Success() {
            // Given
            CategoryId excludeId = new CategoryId(1L);
            when(categoryJpaRepository.existsByNameAndIdNot("전자제품", 1L)).thenReturn(false);

            // When
            boolean result = categoryRepository.existsByNameAndIdNot("전자제품", excludeId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 이름으로 존재 여부 확인 시 예외 발생")
        void existsByNameAndIdNot_WithNullName_ThrowsException() {
            // Given
            CategoryId categoryId = new CategoryId(1L);

            // When & Then
            assertThatThrownBy(() -> categoryRepository.existsByNameAndIdNot(null, categoryId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Not valid category");
        }
    }

    @Nested
    @DisplayName("카테고리 조회 테스트")
    class FindCategoryTests {

        @Test
        @DisplayName("ID로 카테고리 조회 성공")
        void findById_Success() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            Category category = Category.reconstitute(
                    categoryId,
                    "전자제품",
                    null,
                    "ACTIVE"
            );

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryDataAccessMapper.categoryEntityToCategory(categoryEntity)).thenReturn(category);

            // When
            Optional<Category> result = categoryRepository.findById(categoryId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("전자제품");
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
        void findById_NotFound_ReturnsEmpty() {
            // Given
            CategoryId categoryId = new CategoryId(999L);
            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            // When
            Optional<Category> result = categoryRepository.findById(categoryId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ID 목록으로 카테고리들 조회 성공")
        void findAllById_Success() {
            // Given
            CategoryId categoryId1 = new CategoryId(1L);
            CategoryId categoryId2 = new CategoryId(2L);
            List<CategoryId> categoryIds = List.of(categoryId1, categoryId2);

            CategoryEntity entity1 = mock(CategoryEntity.class);
            CategoryEntity entity2 = mock(CategoryEntity.class);
            List<CategoryEntity> entities = List.of(entity1, entity2);

            Category category1 = Category.reconstitute(
                    categoryId1,
                    "전자제품",
                    null,
                    "ACTIVE"
            );

            Category category2 = Category.reconstitute(
                    categoryId2,
                    "도서",
                    null,
                    "INACTIVE"
            );

            when(categoryJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(entities);
            when(categoryDataAccessMapper.categoryEntityToCategory(entity1)).thenReturn(category1);
            when(categoryDataAccessMapper.categoryEntityToCategory(entity2)).thenReturn(category2);

            // When
            List<Category> result = categoryRepository.findAllById(categoryIds);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "도서");
        }
    }

    @Nested
    @DisplayName("상태 업데이트 테스트")
    class UpdateStatusTests {

        @Test
        @DisplayName("카테고리들 상태 일괄 업데이트 성공")
        void updateStatusForIds_Success() {
            // Given
            List<CategoryId> categoryIds = List.of(
                    new CategoryId(1L),
                    new CategoryId(2L),
                    new CategoryId(3L)
            );
            String newStatus = "INACTIVE";

            // When
            categoryRepository.updateStatusForIds(newStatus, categoryIds);

            // Then
            verify(categoryJpaRepository).updateStatusForIds(newStatus, List.of(1L, 2L, 3L));
        }

        @Test
        @DisplayName("null 상태로 업데이트 시 아무것도 하지 않음")
        void updateStatusForIds_WithNullStatus_DoesNothing() {
            // Given
            List<CategoryId> categoryIds = List.of(new CategoryId(1L));

            // When
            categoryRepository.updateStatusForIds(null, categoryIds);

            // Then
            verifyNoInteractions(categoryJpaRepository);
        }

        @Test
        @DisplayName("빈 ID 목록으로 업데이트 시 아무것도 하지 않음")
        void updateStatusForIds_WithEmptyList_DoesNothing() {
            // Given
            String newStatus = "INACTIVE";
            List<CategoryId> emptyList = Collections.emptyList();

            // When
            categoryRepository.updateStatusForIds(newStatus, emptyList);

            // Then
            verifyNoInteractions(categoryJpaRepository);
        }
    }

    @Nested
    @DisplayName("계층 구조 조회 테스트")
    class HierarchyQueryTests {

        @Test
        @DisplayName("하위 트리 조회 성공")
        void findAllSubTreeById_Success() {
            // Given
            CategoryId rootId = new CategoryId(1L);

            CategoryEntity rootEntity = mock(CategoryEntity.class);
            CategoryEntity childEntity = mock(CategoryEntity.class);
            List<CategoryEntity> subTreeEntities = List.of(rootEntity, childEntity);

            Category rootCategory = Category.reconstitute(
                    new CategoryId(1L),
                    "전자제품",
                    null,
                    "ACTIVE"
            );
            Category childCategory = Category.reconstitute(
                    new CategoryId(2L),
                    "노트북",
                    rootId,
                    "ACTIVE"
            );

            when(categoryJpaRepository.findSubTreeByIdNative(1L)).thenReturn(subTreeEntities);
            when(categoryDataAccessMapper.categoryEntityToCategory(rootEntity)).thenReturn(rootCategory);
            when(categoryDataAccessMapper.categoryEntityToCategory(childEntity)).thenReturn(childCategory);

            // When
            List<Category> result = categoryRepository.findAllSubTreeById(rootId);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "노트북");
        }

        @Test
        @DisplayName("특정 상태의 하위 트리 조회 성공")
        void findSubTreeByIdAndStatusIn_Success() {
            // Given
            CategoryId rootId = new CategoryId(1L);
            List<String> statusList = List.of("ACTIVE");

            CategoryEntity activeEntity = mock(CategoryEntity.class);
            List<CategoryEntity> activeEntities = List.of(activeEntity);

            Category activeCategory = Category.reconstitute(
                    new CategoryId(1L),
                    "활성 카테고리",
                    null,
                    "ACTIVE"
            );

            when(categoryJpaRepository.findSubTreeByIdAndStatusInNative(1L, statusList))
                    .thenReturn(activeEntities);
            when(categoryDataAccessMapper.categoryEntityToCategory(activeEntity))
                    .thenReturn(activeCategory);

            // When
            List<Category> result = categoryRepository.findSubTreeByIdAndStatusIn(rootId, statusList);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("활성 카테고리");
            assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("조상 카테고리들 조회 성공")
        void findAllAncestorsById_Success() {
            // Given
            CategoryId categoryId = new CategoryId(3L);

            CategoryEntity grandParentEntity = mock(CategoryEntity.class);
            CategoryEntity parentEntity = mock(CategoryEntity.class);
            CategoryEntity currentEntity = mock(CategoryEntity.class);
            List<CategoryEntity> ancestorEntities = List.of(grandParentEntity, parentEntity, currentEntity);

            Category grandParent = Category.reconstitute(
                    new CategoryId(1L),
                    "전체",
                    null,
                    "ACTIVE"
            );

            Category parent = Category.reconstitute(
                    new CategoryId(2L),
                    "전자제품",
                    new CategoryId(1L),
                    "ACTIVE"
            );

            Category current = Category.reconstitute(
                    new CategoryId(3L),
                    "노트북",
                    new CategoryId(2L),
                    "INACTIVE"
            );

            when(categoryJpaRepository.findAncestorsByIdNative(3L)).thenReturn(ancestorEntities);
            when(categoryDataAccessMapper.categoryEntityToCategory(grandParentEntity)).thenReturn(grandParent);
            when(categoryDataAccessMapper.categoryEntityToCategory(parentEntity)).thenReturn(parent);
            when(categoryDataAccessMapper.categoryEntityToCategory(currentEntity)).thenReturn(current);

            // When
            List<Category> result = categoryRepository.findAllAncestorsById(categoryId);

            // Then
            assertThat(result).hasSize(3);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전체", "전자제품", "노트북");
        }

        @Test
        @DisplayName("깊이 조회 성공")
        void getDepth_Success() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            when(categoryJpaRepository.getDepthByIdNative(1L)).thenReturn(3);

            // When
            int result = categoryRepository.getDepth(categoryId);

            // Then
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("null 깊이 조회 시 0 반환")
        void getDepth_WithNullResult_ReturnsZero() {
            // Given
            CategoryId categoryId = new CategoryId(1L);
            when(categoryJpaRepository.getDepthByIdNative(1L)).thenReturn(null);

            // When
            int result = categoryRepository.getDepth(categoryId);

            // Then
            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("null CategoryId로 깊이 조회 시 예외 발생")
        void getDepth_WithNullId_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> categoryRepository.getDepth(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CategoryId object can not be null");
        }
    }
}