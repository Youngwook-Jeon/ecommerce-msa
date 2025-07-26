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
        @DisplayName("부모가 없는 카테고리 저장 성공")
        void save_WithoutParent_Success() {
            // Given
            Category category = Category.builder()
                    .name("전자제품")
                    .parentId(null)
                    .build();

            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.builder()
                    .categoryId(new CategoryId(1L))
                    .name("전자제품")
                    .parentId(null)
                    .build();

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
        }

        @Test
        @DisplayName("부모가 있는 카테고리 저장 성공")
        void save_WithParent_Success() {
            // Given
            CategoryId parentId = new CategoryId(1L);
            Category category = Category.builder()
                    .name("노트북")
                    .parentId(parentId)
                    .build();

            CategoryEntity parentEntity = mock(CategoryEntity.class);
            CategoryEntity categoryEntity = mock(CategoryEntity.class);
            CategoryEntity savedCategoryEntity = mock(CategoryEntity.class);
            Category savedCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("노트북")
                    .parentId(parentId)
                    .build();

            when(categoryJpaRepository.findById(1L)).thenReturn(Optional.of(parentEntity));
            when(categoryDataAccessMapper.categoryToCategoryEntity(category, parentEntity))
                    .thenReturn(categoryEntity);
            when(categoryJpaRepository.save(categoryEntity)).thenReturn(savedCategoryEntity);
            when(categoryDataAccessMapper.categoryEntityToCategory(savedCategoryEntity))
                    .thenReturn(savedCategory);

            // When
            Category result = categoryRepository.save(category);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("노트북");
            verify(parentEntity).addChild(categoryEntity);
        }

        @Test
        @DisplayName("존재하지 않는 부모 카테고리로 저장 시 예외 발생")
        void save_WithNonExistentParent_ThrowsException() {
            // Given
            CategoryId parentId = new CategoryId(999L);
            Category category = Category.builder()
                    .name("노트북")
                    .parentId(parentId)
                    .build();

            when(categoryJpaRepository.findById(999L)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryRepository.save(category))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("The parent category does not exist");
        }
    }

    @Nested
    @DisplayName("카테고리 일괄 저장 테스트")
    class SaveAllCategoriesTests {

        @Test
        @DisplayName("카테고리 목록 일괄 저장 성공")
        void saveAll_Success() {
            // Given
            Category category1 = Category.builder().name("전자제품").build();
            Category category2 = Category.builder().name("도서").build();
            List<Category> categories = List.of(category1, category2);

            CategoryEntity entity1 = mock(CategoryEntity.class);
            CategoryEntity entity2 = mock(CategoryEntity.class);
            CategoryEntity savedEntity1 = mock(CategoryEntity.class);
            CategoryEntity savedEntity2 = mock(CategoryEntity.class);

            Category savedCategory1 = Category.builder()
                    .categoryId(new CategoryId(1L))
                    .name("전자제품")
                    .build();
            Category savedCategory2 = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("도서")
                    .build();

            when(categoryDataAccessMapper.categoryToCategoryEntitySimple(any(Category.class)))
                    .thenReturn(entity1, entity2);
            when(categoryJpaRepository.saveAll(anyList())).thenReturn(List.of(savedEntity1, savedEntity2));
            when(categoryDataAccessMapper.categoryEntityToCategory(any(CategoryEntity.class)))
                    .thenReturn(savedCategory1, savedCategory2);

            // When
            List<Category> result = categoryRepository.saveAll(categories);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Category::getName)
                    .containsExactly("전자제품", "도서");

            verify(categoryDataAccessMapper, times(2)).categoryToCategoryEntitySimple(any(Category.class));
            verify(categoryJpaRepository, times(1)).saveAll(anyList());
            verify(categoryDataAccessMapper, times(2)).categoryEntityToCategory(any(CategoryEntity.class));
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

        @Test
        @DisplayName("null CategoryId로 존재 여부 확인 시 예외 발생")
        void existsByNameAndIdNot_WithNullCategoryId_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> categoryRepository.existsByNameAndIdNot("전자제품", null))
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
            Category category = Category.builder()
                    .categoryId(categoryId)
                    .name("전자제품")
                    .build();

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
        @DisplayName("null ID로 조회 시 예외 발생")
        void findById_WithNullId_ThrowsException() {
            // When & Then
            assertThatThrownBy(() -> categoryRepository.findById(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CategoryId object can not be null");
        }
    }

    @Nested
    @DisplayName("하위 트리 조회 테스트")
    class FindSubTreeTests {

        @Test
        @DisplayName("하위 트리 조회 성공")
        void findAllSubTreeById_Success() {
            // Given
            CategoryId rootId = new CategoryId(1L);

            CategoryEntity rootEntity = mock(CategoryEntity.class);
            CategoryEntity childEntity = mock(CategoryEntity.class);
            List<CategoryEntity> subTreeEntities = List.of(rootEntity, childEntity);

            Category rootCategory = Category.builder()
                    .categoryId(new CategoryId(1L))
                    .name("전자제품")
                    .build();
            Category childCategory = Category.builder()
                    .categoryId(new CategoryId(2L))
                    .name("노트북")
                    .build();

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
        @DisplayName("null ID로 하위 트리 조회 시 빈 목록 반환")
        void findAllSubTreeById_WithNullId_ReturnsEmptyList() {
            // When
            List<Category> result = categoryRepository.findAllSubTreeById(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 ID로 하위 트리 조회 시 빈 목록 반환")
        void findAllSubTreeById_NotFound_ReturnsEmptyList() {
            // Given
            CategoryId categoryId = new CategoryId(999L);
            when(categoryJpaRepository.findSubTreeByIdNative(999L)).thenReturn(Collections.emptyList());

            // When
            List<Category> result = categoryRepository.findAllSubTreeById(categoryId);

            // Then
            assertThat(result).isEmpty();
        }
    }
}