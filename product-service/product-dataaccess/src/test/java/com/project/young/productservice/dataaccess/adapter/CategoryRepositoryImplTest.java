package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryAggregateMapper;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryRepositoryImplTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    @Mock
    private CategoryDataAccessMapper categoryDataAccessMapper;

    @Mock
    private EntityManager entityManager;

    private CategoryAggregateMapper categoryAggregateMapper;

    private CategoryRepositoryImpl categoryRepository;

    @BeforeEach
    void setUp() {
        categoryAggregateMapper = new CategoryAggregateMapper();
        categoryRepository = new CategoryRepositoryImpl(
                categoryJpaRepository,
                categoryDataAccessMapper,
                categoryAggregateMapper,
                entityManager
        );
    }

    @Nested
    @DisplayName("카테고리 저장 테스트")
    class SaveCategoryTests {

        @Test
        @DisplayName("null 카테고리 저장 시 예외 발생")
        void save_NullCategory_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.insert(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("category must not be null");

            verifyNoInteractions(categoryJpaRepository, categoryDataAccessMapper, entityManager);
        }

        @Test
        @DisplayName("부모가 없는 새 카테고리 저장 성공")
        void save_NewCategoryWithoutParent_Success() {
            Category category = Category.builder()
                    .name("전자제품")
                    .status(CategoryStatus.ACTIVE)
                    .parentId(null)
                    .build();

            CategoryEntity toPersist = CategoryEntity.builder()
                    .name("전자제품")
                    .status(CategoryStatusEntity.ACTIVE)
                    .build();

            when(categoryDataAccessMapper.categoryToCategoryEntity(category, null))
                    .thenReturn(toPersist);
            doAnswer(invocation -> {
                CategoryEntity e = invocation.getArgument(0);
                e.setId(1L);
                return null;
            }).when(entityManager).persist(any(CategoryEntity.class));

            Category result = categoryRepository.insert(category);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("전자제품");
            assertThat(result.getId().getValue()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(CategoryStatus.ACTIVE);

            verify(categoryDataAccessMapper).categoryToCategoryEntity(category, null);
            verify(entityManager).persist(toPersist);
            verify(categoryJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("부모가 있는 새 카테고리 저장 성공")
        void save_NewCategoryWithParent_Success() {
            CategoryId parentId = new CategoryId(1L);
            Category category = Category.builder()
                    .name("노트북")
                    .status(CategoryStatus.ACTIVE)
                    .parentId(parentId)
                    .build();

            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            when(parentRefEntity.getId()).thenReturn(1L);

            CategoryEntity toPersist = CategoryEntity.builder()
                    .name("노트북")
                    .status(CategoryStatusEntity.ACTIVE)
                    .parent(parentRefEntity)
                    .build();

            when(categoryJpaRepository.getReferenceById(1L)).thenReturn(parentRefEntity);
            when(categoryDataAccessMapper.categoryToCategoryEntity(category, parentRefEntity))
                    .thenReturn(toPersist);
            doAnswer(invocation -> {
                CategoryEntity e = invocation.getArgument(0);
                e.setId(2L);
                return null;
            }).when(entityManager).persist(any(CategoryEntity.class));

            Category result = categoryRepository.insert(category);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("노트북");
            assertThat(result.getId().getValue()).isEqualTo(2L);
            assertThat(result.getParentId()).contains(parentId);
            assertThat(result.getStatus()).isEqualTo(CategoryStatus.ACTIVE);

            verify(categoryJpaRepository).getReferenceById(1L);
            verify(categoryDataAccessMapper).categoryToCategoryEntity(category, parentRefEntity);
            verify(entityManager).persist(toPersist);
            verify(categoryJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 카테고리 업데이트 성공")
        void save_UpdateExistingCategory_Success() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryId parentId = new CategoryId(2L);
            Category category = Category.reconstitute(
                    categoryId,
                    "수정된 카테고리",
                    parentId,
                    CategoryStatus.INACTIVE
            );

            CategoryEntity existingEntity = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(categoryJpaRepository.getReferenceById(2L)).thenReturn(parentRefEntity);
            when(categoryDataAccessMapper.toEntityStatus(CategoryStatus.INACTIVE))
                    .thenReturn(CategoryStatusEntity.INACTIVE);

            categoryRepository.update(category);

            verify(categoryJpaRepository).findById(1L);
            verify(categoryJpaRepository).getReferenceById(2L);
            verify(existingEntity).setName("수정된 카테고리");
            verify(categoryDataAccessMapper).toEntityStatus(CategoryStatus.INACTIVE);
            verify(existingEntity).setStatus(CategoryStatusEntity.INACTIVE);
            verify(existingEntity).setParent(parentRefEntity);
            verify(categoryJpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("존재하지 않는 기존 카테고리 업데이트 시 CategoryNotFoundException 발생")
        void save_UpdateNonExistentCategory_ThrowsException() {
            CategoryId categoryId = new CategoryId(999L);
            Category category = Category.reconstitute(
                    categoryId,
                    "존재하지 않는 카테고리",
                    null,
                    CategoryStatus.ACTIVE
            );

            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryRepository.update(category))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category not found: 999");

            verify(categoryJpaRepository).findById(999L);
            verify(categoryJpaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("카테고리 일괄 저장 테스트")
    class SaveAllCategoriesTests {

        @Test
        @DisplayName("null 목록으로 일괄 저장 시 예외 발생")
        void saveAll_WithNullList_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.updateAll(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categories must not be null");

            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("빈 목록으로 일괄 저장 시 빈 목록 반환")
        void saveAll_WithEmptyList_ReturnsEmptyList() {
            categoryRepository.updateAll(Collections.emptyList());

            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("null 요소를 포함한 목록으로 일괄 저장 시 예외 발생")
        void saveAll_WithNullElement_ThrowsException() {
            List<Category> categories = new ArrayList<>();
            categories.add(null);

            assertThatThrownBy(() -> categoryRepository.updateAll(categories))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categories must not contain null elements");

            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("ID 없는 카테고리 포함 시 updateAll 예외 발생")
        void saveAll_NewCategories_Success() {
            Category category1 = Category.builder()
                    .name("전자제품")
                    .status(CategoryStatus.ACTIVE)
                    .build();

            Category category2 = Category.builder()
                    .name("도서")
                    .status(CategoryStatus.ACTIVE)
                    .parentId(new CategoryId(1L))
                    .build();

            List<Category> categories = List.of(category1, category2);

            assertThatThrownBy(() -> categoryRepository.updateAll(categories))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("category id must not be null for updateAll");

            verify(categoryJpaRepository).findAllById(Collections.emptyList());
            verify(categoryJpaRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("기존 카테고리들 일괄 업데이트 성공")
        void saveAll_UpdateExistingCategories_Success() {
            CategoryId categoryId1 = new CategoryId(1L);
            CategoryId categoryId2 = new CategoryId(2L);

            Category category1 = Category.reconstitute(
                    categoryId1,
                    "수정된 전자제품",
                    null,
                    CategoryStatus.INACTIVE
            );

            Category category2 = Category.reconstitute(
                    categoryId2,
                    "수정된 도서",
                    new CategoryId(3L),
                    CategoryStatus.ACTIVE
            );

            List<Category> categories = List.of(category1, category2);

            CategoryEntity existingEntity1 = mock(CategoryEntity.class);
            CategoryEntity existingEntity2 = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);

            when(categoryJpaRepository.findAllById(List.of(1L, 2L)))
                    .thenReturn(List.of(existingEntity1, existingEntity2));
            when(existingEntity1.getId()).thenReturn(1L);
            when(existingEntity2.getId()).thenReturn(2L);

            when(categoryJpaRepository.getReferenceById(3L)).thenReturn(parentRefEntity);

            categoryRepository.updateAll(categories);

            verify(categoryJpaRepository).findAllById(List.of(1L, 2L));
            verify(categoryDataAccessMapper).updateEntityFromDomain(category1, existingEntity1);
            verify(categoryDataAccessMapper).updateEntityFromDomain(category2, existingEntity2);
            verify(categoryJpaRepository).getReferenceById(3L);
            verify(existingEntity2).setParent(parentRefEntity);
            verify(categoryJpaRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("존재하지 않는 기존 카테고리가 포함된 경우 CategoryNotFoundException 발생")
        void saveAll_WithNonExistentExistingCategory_ThrowsException() {
            Category category = Category.reconstitute(
                    new CategoryId(999L),
                    "존재하지 않는 카테고리",
                    null,
                    CategoryStatus.ACTIVE
            );

            List<Category> categories = List.of(category);

            when(categoryJpaRepository.findAllById(List.of(999L)))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> categoryRepository.updateAll(categories))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id 999 not found for update in updateAll");

            verify(categoryJpaRepository).findAllById(List.of(999L));
            verify(categoryJpaRepository, never()).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("존재 여부 확인 테스트")
    class ExistenceCheckTests {

        @Test
        @DisplayName("이름으로 카테고리 존재 여부 확인")
        void existsByName_Success() {
            when(categoryJpaRepository.existsByName("전자제품")).thenReturn(true);

            boolean result = categoryRepository.existsByName("전자제품");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("blank 이름으로 카테고리 존재 여부 확인 시 예외 발생")
        void existsByName_Blank_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.existsByName(" "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name must not be null or blank");

            verifyNoInteractions(categoryJpaRepository);
        }

        @Test
        @DisplayName("ID로 카테고리 존재 여부 확인")
        void existsById_Success() {
            CategoryId categoryId = new CategoryId(1L);
            when(categoryJpaRepository.existsById(1L)).thenReturn(true);

            boolean result = categoryRepository.existsById(categoryId);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("null CategoryId로 존재 여부 확인 시 예외 발생")
        void existsById_WithNullId_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.existsById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CategoryId object can not be null");

            verifyNoInteractions(categoryJpaRepository);
        }

        @Test
        @DisplayName("특정 ID 제외하고 이름으로 존재 여부 확인")
        void existsByNameAndIdNot_Success() {
            CategoryId excludeId = new CategoryId(1L);
            when(categoryJpaRepository.existsByNameAndIdNot("전자제품", 1L)).thenReturn(false);

            boolean result = categoryRepository.existsByNameAndIdNot("전자제품", excludeId);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("카테고리 조회 테스트")
    class FindCategoryTests {

        @Test
        @DisplayName("ID로 카테고리 조회 성공")
        void findById_Success() {
            CategoryId categoryId = new CategoryId(1L);
            CategoryEntity categoryEntity = CategoryEntity.builder()
                    .id(1L)
                    .name("전자제품")
                    .status(CategoryStatusEntity.ACTIVE)
                    .build();

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));

            Optional<Category> result = categoryRepository.findById(categoryId);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("전자제품");
            assertThat(result.get().getStatus()).isEqualTo(CategoryStatus.ACTIVE);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 빈 Optional 반환")
        void findById_NotFound_ReturnsEmpty() {
            CategoryId categoryId = new CategoryId(999L);
            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<Category> result = categoryRepository.findById(categoryId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ID 목록으로 카테고리들 조회 성공")
        void findAllById_Success() {
            CategoryId categoryId1 = new CategoryId(1L);
            CategoryId categoryId2 = new CategoryId(2L);
            List<CategoryId> categoryIds = List.of(categoryId1, categoryId2);

            CategoryEntity entity1 = CategoryEntity.builder()
                    .id(1L)
                    .name("전자제품")
                    .status(CategoryStatusEntity.ACTIVE)
                    .build();
            CategoryEntity entity2 = CategoryEntity.builder()
                    .id(2L)
                    .name("도서")
                    .status(CategoryStatusEntity.INACTIVE)
                    .build();
            List<CategoryEntity> entities = List.of(entity1, entity2);

            when(categoryJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(entities);

            List<Category> result = categoryRepository.findAllById(categoryIds);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "도서");
            assertThat(result).extracting(Category::getStatus)
                    .containsExactly(CategoryStatus.ACTIVE, CategoryStatus.INACTIVE);
        }

        @Test
        @DisplayName("null ID 목록으로 조회 시 예외 발생")
        void findAllById_NullList_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.findAllById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoryIdList must not be null");

            verifyNoInteractions(categoryJpaRepository);
        }

        @Test
        @DisplayName("null 요소가 포함된 ID 목록으로 조회 시 예외 발생")
        void findAllById_ListContainsNull_ThrowsException() {
            List<CategoryId> categoryIds = new ArrayList<>();
            categoryIds.add(new CategoryId(1L));
            categoryIds.add(null);

            assertThatThrownBy(() -> categoryRepository.findAllById(categoryIds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoryIdList must not contain null elements");

            verifyNoInteractions(categoryJpaRepository);
        }
    }

    @Nested
    @DisplayName("계층 구조 조회 테스트")
    class HierarchyQueryTests {

        @Test
        @DisplayName("하위 트리 조회 성공")
        void findAllSubTreeById_Success() {
            CategoryId rootId = new CategoryId(1L);

            CategoryEntity rootEntity = CategoryEntity.builder()
                    .id(1L)
                    .name("전자제품")
                    .status(CategoryStatusEntity.ACTIVE)
                    .build();
            CategoryEntity childEntity = CategoryEntity.builder()
                    .id(2L)
                    .name("노트북")
                    .status(CategoryStatusEntity.ACTIVE)
                    .parent(rootEntity)
                    .build();
            List<CategoryEntity> subTreeEntities = List.of(rootEntity, childEntity);

            when(categoryJpaRepository.findSubTreeByIdNative(1L)).thenReturn(subTreeEntities);

            List<Category> result = categoryRepository.findAllSubTreeById(rootId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "노트북");
            assertThat(result.get(1).getParentId()).contains(rootId);
        }

        @Test
        @DisplayName("null categoryId로 하위 트리 조회 시 예외 발생")
        void findAllSubTreeById_Null_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.findAllSubTreeById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoryId must not be null");

            verifyNoInteractions(categoryJpaRepository);
        }
    }
}
