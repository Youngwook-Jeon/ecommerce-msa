package com.project.young.productservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.CategoryId;
import com.project.young.productservice.dataaccess.entity.CategoryEntity;
import com.project.young.productservice.dataaccess.enums.CategoryStatusEntity;
import com.project.young.productservice.dataaccess.mapper.CategoryDataAccessMapper;
import com.project.young.productservice.dataaccess.repository.CategoryJpaRepository;
import com.project.young.productservice.domain.entity.Category;
import com.project.young.productservice.domain.exception.CategoryNotFoundException;
import com.project.young.productservice.domain.valueobject.CategoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
        @DisplayName("null 카테고리 저장 시 예외 발생")
        void save_NullCategory_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.save(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("category must not be null");

            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("부모가 없는 새 카테고리 저장 성공")
        void save_NewCategoryWithoutParent_Success() {
            // Given
            Category category = Category.builder()
                    .name("전자제품")
                    .status(CategoryStatus.ACTIVE)
                    .parentId(null)
                    .build();

            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    new CategoryId(1L),
                    "전자제품",
                    null,
                    CategoryStatus.ACTIVE
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
            assertThat(result.getStatus()).isEqualTo(CategoryStatus.ACTIVE);

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
                    .status(CategoryStatus.ACTIVE)
                    .parentId(parentId)
                    .build();

            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    new CategoryId(2L),
                    "노트북",
                    parentId,
                    CategoryStatus.ACTIVE
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
            assertThat(result.getStatus()).isEqualTo(CategoryStatus.ACTIVE);

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
                    CategoryStatus.INACTIVE
            );

            CategoryEntity existingEntity = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity savedEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.reconstitute(
                    categoryId,
                    "수정된 카테고리",
                    parentId,
                    CategoryStatus.INACTIVE
            );

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(existingEntity));
            when(categoryJpaRepository.getReferenceById(2L)).thenReturn(parentRefEntity);
            when(categoryDataAccessMapper.toEntityStatus(CategoryStatus.INACTIVE))
                    .thenReturn(CategoryStatusEntity.INACTIVE);
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
            verify(categoryDataAccessMapper).toEntityStatus(CategoryStatus.INACTIVE);
            verify(existingEntity).setStatus(CategoryStatusEntity.INACTIVE);
            verify(existingEntity).setParent(parentRefEntity);
            verify(categoryJpaRepository).save(existingEntity);
        }

        @Test
        @DisplayName("존재하지 않는 기존 카테고리 업데이트 시 CategoryNotFoundException 발생")
        void save_UpdateNonExistentCategory_ThrowsException() {
            // Given
            CategoryId categoryId = new CategoryId(999L);
            Category category = Category.reconstitute(
                    categoryId,
                    "존재하지 않는 카테고리",
                    null,
                    CategoryStatus.ACTIVE
            );

            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryRepository.save(category))
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
            assertThatThrownBy(() -> categoryRepository.saveAll(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categories must not be null");

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

        @Test
        @DisplayName("null 요소를 포함한 목록으로 일괄 저장 시 예외 발생")
        void saveAll_WithNullElement_ThrowsException() {
            // Given
            List<Category> categories = new ArrayList<>();
            categories.add(null);

            assertThatThrownBy(() -> categoryRepository.saveAll(categories))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categories must not contain null elements");

            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("새 카테고리들 일괄 저장 성공")
        void saveAll_NewCategories_Success() {
            // Given
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

            CategoryEntity entity1 = mock(CategoryEntity.class);
            CategoryEntity entity2 = mock(CategoryEntity.class);
            CategoryEntity parentRefEntity = mock(CategoryEntity.class);
            CategoryEntity savedEntity1 = mock(CategoryEntity.class);
            CategoryEntity savedEntity2 = mock(CategoryEntity.class);

            Category savedCategory1 = Category.reconstitute(
                    new CategoryId(10L),
                    "전자제품",
                    null,
                    CategoryStatus.ACTIVE
            );

            Category savedCategory2 = Category.reconstitute(
                    new CategoryId(11L),
                    "도서",
                    new CategoryId(1L),
                    CategoryStatus.ACTIVE
            );

            when(categoryJpaRepository.findAllById(Collections.emptyList()))
                    .thenReturn(Collections.emptyList());

            when(categoryDataAccessMapper.categoryToCategoryEntity(any(Category.class), eq(null)))
                    .thenReturn(entity1, entity2);

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

            Category savedCategory1 = Category.reconstitute(
                    categoryId1,
                    "수정된 전자제품",
                    null,
                    CategoryStatus.INACTIVE
            );

            Category savedCategory2 = Category.reconstitute(
                    categoryId2,
                    "수정된 도서",
                    new CategoryId(3L),
                    CategoryStatus.ACTIVE
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
        @DisplayName("존재하지 않는 기존 카테고리가 포함된 경우 CategoryNotFoundException 발생")
        void saveAll_WithNonExistentExistingCategory_ThrowsException() {
            // Given
            Category category = Category.reconstitute(
                    new CategoryId(999L),
                    "존재하지 않는 카테고리",
                    null,
                    CategoryStatus.ACTIVE
            );

            List<Category> categories = List.of(category);

            when(categoryJpaRepository.findAllById(List.of(999L)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            assertThatThrownBy(() -> categoryRepository.saveAll(categories))
                    .isInstanceOf(CategoryNotFoundException.class)
                    .hasMessageContaining("Category with id 999 not found for update in saveAll");

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
            // Given
            when(categoryJpaRepository.existsByName("전자제품")).thenReturn(true);

            // When
            boolean result = categoryRepository.existsByName("전자제품");

            // Then
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

            verifyNoInteractions(categoryJpaRepository);
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
                    CategoryStatus.ACTIVE
            );

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryDataAccessMapper.categoryEntityToCategory(categoryEntity)).thenReturn(category);

            // When
            Optional<Category> result = categoryRepository.findById(categoryId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("전자제품");
            assertThat(result.get().getStatus()).isEqualTo(CategoryStatus.ACTIVE);
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
                    CategoryStatus.ACTIVE
            );

            Category category2 = Category.reconstitute(
                    categoryId2,
                    "도서",
                    null,
                    CategoryStatus.INACTIVE
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
            //Given
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
            CategoryStatus newStatus = CategoryStatus.INACTIVE;

            when(categoryDataAccessMapper.toEntityStatus(newStatus))
                    .thenReturn(CategoryStatusEntity.INACTIVE);

            // When
            categoryRepository.updateStatusForIds(newStatus, categoryIds);

            // Then
            verify(categoryDataAccessMapper).toEntityStatus(newStatus);
            verify(categoryJpaRepository).updateStatusForIds(CategoryStatusEntity.INACTIVE, List.of(1L, 2L, 3L));
        }

        @Test
        @DisplayName("null status로 업데이트 시 예외 발생")
        void updateStatusForIds_WithNullStatus_ThrowsException() {
            List<CategoryId> categoryIds = List.of(new CategoryId(1L));

            assertThatThrownBy(() -> categoryRepository.updateStatusForIds(null, categoryIds))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status or categoryIdList must not be null");

            verifyNoInteractions(categoryJpaRepository);
        }

        @Test
        @DisplayName("빈 ID 목록으로 업데이트 시 아무것도 하지 않음")
        void updateStatusForIds_WithEmptyList_DoesNothing() {
            // Given
            CategoryStatus newStatus = CategoryStatus.INACTIVE;
            List<CategoryId> emptyList = Collections.emptyList();

            // When
            categoryRepository.updateStatusForIds(newStatus, emptyList);

            // Then
            verifyNoInteractions(categoryJpaRepository);
            verifyNoInteractions(categoryDataAccessMapper);
        }

        @Test
        @DisplayName("ID 목록에 null 요소가 포함되면 예외 발생")
        void updateStatusForIds_WithNullElement_ThrowsException() {
            CategoryStatus newStatus = CategoryStatus.INACTIVE;

            List<CategoryId> listContainsNull = new ArrayList<>();
            listContainsNull.add(new CategoryId(1L));
            listContainsNull.add(null);

            assertThatThrownBy(() -> categoryRepository.updateStatusForIds(newStatus, listContainsNull))
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
            // Given
            CategoryId rootId = new CategoryId(1L);

            CategoryEntity rootEntity = mock(CategoryEntity.class);
            CategoryEntity childEntity = mock(CategoryEntity.class);
            List<CategoryEntity> subTreeEntities = List.of(rootEntity, childEntity);

            Category rootCategory = Category.reconstitute(
                    new CategoryId(1L),
                    "전자제품",
                    null,
                    CategoryStatus.ACTIVE
            );
            Category childCategory = Category.reconstitute(
                    new CategoryId(2L),
                    "노트북",
                    rootId,
                    CategoryStatus.ACTIVE
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
        @DisplayName("null categoryId로 하위 트리 조회 시 예외 발생")
        void findAllSubTreeById_Null_ThrowsException() {
            assertThatThrownBy(() -> categoryRepository.findAllSubTreeById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("categoryId must not be null");

            verifyNoInteractions(categoryJpaRepository);
        }
    }
}